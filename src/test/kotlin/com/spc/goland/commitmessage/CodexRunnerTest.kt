package com.spc.goland.commitmessage

import com.intellij.execution.Platform
import com.intellij.openapi.util.SystemInfoRt
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CodexRunnerTest {
    @Test
    fun generatesMessageWithExplicitExecutableAndCleansTemporaryFiles() {
        Fixture().use { fixture ->
            val prompt = "Generate a commit message from the selected changes."
            val message = fixture.generate(model = "model with spaces", prompt = prompt)

            assertEquals("fix: generated message", message)
            assertEquals(prompt, Files.readString(fixture.directory.resolve("prompt.txt")))
            val arguments = Files.readAllLines(fixture.directory.resolve("arguments.txt"))
            assertEquals("exec", arguments.first())
            assertEquals("read-only", arguments[arguments.indexOf("--sandbox") + 1])
            assertEquals(fixture.directory.toString(), arguments[arguments.indexOf("--cd") + 1])
            assertEquals("model with spaces", arguments[arguments.indexOf("-m") + 1])
            assertTrue(arguments.contains("--ephemeral"))
            assertTrue(arguments.contains("--skip-git-repo-check"))
            assertEquals("-", arguments.last())
            fixture.assertTemporaryFilesDeleted()
        }
    }

    @Test
    fun leavesModelSelectionToCodexWhenNoModelIsSpecified() {
        Fixture().use { fixture ->
            fixture.generate(model = null)
            assertFalse(Files.readAllLines(fixture.directory.resolve("arguments.txt")).contains("-m"))
            fixture.assertTemporaryFilesDeleted()
        }
    }

    @Test
    fun reportsNonzeroExitWithoutReturningSensitiveLogs() {
        Fixture("nonzero").use { fixture ->
            val error = assertFailsWith<IllegalStateException> { fixture.generate() }
            assertTrue(error.message.orEmpty().contains("7"))
            assertTrue(error.message.orEmpty().contains("认证"))
            assertFalse(error.message.orEmpty().contains("sensitive-test-log"))
            fixture.assertTemporaryFilesDeleted()
        }
    }

    @Test
    fun mapsStderrKeywordsToHintsWithoutEchoingCliOutput() {
        assertEquals(
            "Codex 生成失败（退出码 2）。Codex CLI 不支持当前调用参数，可能是版本过旧，请升级 Codex 后重试。",
            CodexRunner.failureMessage(2, "error: unexpected argument '--ephemeral' found\nUsage: codex exec [OPTIONS]"),
        )
        assertTrue(
            CodexRunner.failureMessage(1, "Error: not logged in").contains("codex login"),
        )
        assertTrue(
            CodexRunner.failureMessage(1, "{\"error\":{\"message\":\"unknown model: foo\"}}").contains("模型"),
        )
        assertEquals(
            "Codex 生成失败（退出码 1）。请检查 Codex 认证和模型配置。",
            CodexRunner.failureMessage(1, "opaque failure"),
        )
    }

    @Test
    fun reportsUsageErrorsFromRealCliStderr() {
        Fixture("usage-error").use { fixture ->
            val error = assertFailsWith<IllegalStateException> { fixture.generate() }
            assertTrue(error.message.orEmpty().contains("版本过旧"))
            assertFalse(error.message.orEmpty().contains("sensitive-test-log"))
            fixture.assertTemporaryFilesDeleted()
        }
    }

    @Test
    fun rejectsEmptyOutputAndCleansTemporaryFiles() {
        Fixture("empty").use { fixture ->
            val error = assertFailsWith<IllegalStateException> { fixture.generate() }
            assertTrue(error.message.orEmpty().contains("没有返回提交说明"))
            fixture.assertTemporaryFilesDeleted()
        }
    }

    @Test
    fun largeStdoutAndStderrDoNotBlockGeneration() {
        Fixture("large-output").use { fixture ->
            assertEquals("fix: generated message", fixture.generate())
            fixture.assertTemporaryFilesDeleted()
        }
    }

    @Test
    fun timeoutStopsProcessAndChildEvenWhenLargeStdinIsNeverRead() {
        Fixture("wait").use { fixture ->
            val started = System.nanoTime()
            val error = assertFailsWith<IllegalStateException> {
                fixture.generate(prompt = "x".repeat(4 * 1024 * 1024), timeoutMillis = 2_000)
            }
            assertTrue(error.message.orEmpty().contains("超时"))
            assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started) < 10)
            fixture.assertProcessesStopped()
            fixture.assertTemporaryFilesDeleted()
        }
    }

    @Test
    fun cancellationStopsProcessAndChildAndCleansTemporaryFiles() {
        Fixture("wait").use { fixture ->
            val error = assertFailsWith<CancellationException> {
                fixture.generate(prompt = "x".repeat(4 * 1024 * 1024)) {
                    if (Files.exists(fixture.directory.resolve("processes.txt"))) {
                        throw CancellationException("user cancelled")
                    }
                }
            }
            assertEquals("user cancelled", error.message)
            fixture.assertProcessesStopped()
            fixture.assertTemporaryFilesDeleted()
        }
    }

    @Test
    fun cancellationBeforeStartingDoesNotCreateTemporaryFiles() {
        Fixture().use { fixture ->
            assertFailsWith<CancellationException> {
                fixture.generate { throw CancellationException("user cancelled") }
            }
            assertFalse(Files.exists(fixture.directory.resolve("arguments.txt")))
            fixture.assertTemporaryFilesDeleted()
        }
    }

    @Test
    fun explicitExecutableTakesPriorityOverPathAndInvalidExplicitPathDoesNotFallBack() {
        Fixture().use { fixture ->
            assertEquals(
                fixture.executable,
                CodexRunner.findCodex(fixture.executable.toString(), emptyMap()),
            )
            assertFailsWith<IllegalStateException> {
                CodexRunner.findCodex(fixture.directory.resolve("missing-codex").toString(), fixture.environment)
            }
            fixture.assertTemporaryFilesDeleted()
        }
    }

    @Test
    fun findsCodexUsingPathAndPreservesItsDirectoryForNode() {
        Fixture().use { fixture ->
            assertEquals(fixture.executable, CodexRunner.findCodex(null, fixture.environment))
            assertEquals("fix: generated message", fixture.generate(executablePath = null))

            val path = Files.readString(fixture.directory.resolve("path.txt"))
            assertEquals(fixture.executable.parent.toString(), path.split(File.pathSeparatorChar).first())
            fixture.assertTemporaryFilesDeleted()
        }
    }

    @Test
    fun reportsMissingCodexWithoutCreatingTemporaryFiles() {
        Fixture().use { fixture ->
            assertFailsWith<IllegalStateException> { CodexRunner.findCodex(null, mapOf("PATH" to "")) }
            fixture.assertTemporaryFilesDeleted()
        }
    }

    @Test
    fun prependsExplicitExecutableDirectoryUsingPlatformPathSeparator() {
        val executable = Path.of("codex bin", "codex").toAbsolutePath()
        val existingPath = Path.of("other bin").toAbsolutePath().toString()
        val command = CodexRunner.commandLine(executable, null, "", Path.of("output.txt"), mapOf("PATH" to existingPath))

        assertEquals("${executable.parent}${File.pathSeparator}$existingPath", command.environment["PATH"])
        assertFalse(command.parametersList.list.contains("--cd"))
        assertFalse(command.parametersList.list.contains("-m"))
    }

    @Test
    fun startupFailureAlsoCleansInputAndOutputFiles() {
        Fixture().use { fixture ->
            val invalid = fixture.directory.resolve(if (SystemInfoRt.isWindows) "invalid.exe" else "invalid-codex")
            Files.writeString(invalid, "#!/missing-codex-interpreter\n")
            if (!SystemInfoRt.isWindows) assertTrue(invalid.toFile().setExecutable(true))

            assertFailsWith<Exception> { fixture.generate(executablePath = invalid.toString()) }
            fixture.assertTemporaryFilesDeleted()
        }
    }

    @Test
    fun wrapsWindowsCommandScriptsWithPlatformCommandLineEscaping() {
        val executable = Path.of("codex bin", "codex.cmd").toAbsolutePath()
        val output = Path.of("output with spaces.txt").toAbsolutePath()
        val command = CodexRunner.commandLine(executable, "project with spaces", null, output, emptyMap(), windows = true)

        assertEquals("cmd.exe", command.exePath)
        assertEquals(listOf("/d", "/c", executable.toString()), command.parametersList.list.take(3))
        assertTrue(command.parametersList.list.contains("project with spaces"))
        assertFalse(command.parametersList.list.contains("-m"))
        assertTrue(command.getPreparedCommandLine(Platform.WINDOWS).contains("\"project with spaces\""))
    }

    private class Fixture(mode: String = "success") : AutoCloseable {
        val directory: Path = Files.createTempDirectory("codex runner test ").toAbsolutePath()
        private val tempDirectory: Path
        private val binDirectory: Path
        val executable: Path
        val environment: Map<String, String>

        init {
            try {
                tempDirectory = Files.createDirectory(directory.resolve("temporary files"))
                binDirectory = Files.createDirectory(directory.resolve("bin with spaces"))
                executable = binDirectory.resolve(if (SystemInfoRt.isWindows) "codex.cmd" else "codex")
                val source = directory.resolve("FakeCodex.java")
                Files.writeString(source, fakeCodexSource)
                val javaBin = Path.of(System.getProperty("java.home"), "bin")
                val javac = javaBin.resolve(if (SystemInfoRt.isWindows) "javac.exe" else "javac")
                val compilerLog = directory.resolve("compiler.log")
                // The IDE test classloader does not expose the system compiler.
                val compiler = ProcessBuilder(javac.toString(), "-encoding", "UTF-8", "-d", directory.toString(), source.toString())
                    .redirectErrorStream(true)
                    .redirectOutput(compilerLog.toFile())
                    .start()
                try {
                    assertTrue(compiler.waitFor(30, TimeUnit.SECONDS), "Fake CLI compilation timed out")
                    assertEquals(0, compiler.exitValue(), Files.readString(compilerLog))
                } finally {
                    if (compiler.isAlive) {
                        compiler.destroyForcibly()
                        compiler.waitFor(5, TimeUnit.SECONDS)
                    }
                }
                val java = javaBin.resolve(if (SystemInfoRt.isWindows) "java.exe" else "java")
                val script = if (SystemInfoRt.isWindows) {
                    "@echo off\r\n\"$java\" -cp \"$directory\" FakeCodex %*\r\n"
                } else {
                    fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
                    "#!/bin/sh\nexec ${quote(java.toString())} -cp ${quote(directory.toString())} FakeCodex \"\$@\"\n"
                }
                Files.writeString(executable, script)
                if (!SystemInfoRt.isWindows) assertTrue(executable.toFile().setExecutable(true))
                val values = System.getenv().toMutableMap()
                val pathKey = values.keys.firstOrNull { it.equals("PATH", ignoreCase = SystemInfoRt.isWindows) } ?: "PATH"
                values[pathKey] = "$binDirectory${File.pathSeparator}${values[pathKey].orEmpty()}"
                values["FAKE_CODEX_MODE"] = mode
                values["FAKE_CODEX_DIRECTORY"] = directory.toString()
                environment = values
            } catch (error: Throwable) {
                if (!directory.toFile().deleteRecursively()) error.addSuppressed(IllegalStateException("Failed to clean up fake CLI fixture"))
                throw error
            }
        }

        fun generate(
            model: String? = "test-model",
            prompt: String = "Generate a message.",
            timeoutMillis: Long = 10_000,
            executablePath: String? = executable.toString(),
            checkCanceled: () -> Unit = {},
        ): String = CodexRunner.generate(
            directory.toString(), model, prompt, executablePath, checkCanceled,
            timeoutMillis, tempDirectory, environment,
        )

        fun assertTemporaryFilesDeleted() {
            Files.list(tempDirectory).use { assertEquals(0L, it.count()) }
        }

        fun assertProcessesStopped() {
            val processes = Files.readAllLines(directory.resolve("processes.txt")).map(String::toLong)
            assertEquals(2, processes.size)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (processes.any { ProcessHandle.of(it).map(ProcessHandle::isAlive).orElse(false) } && System.nanoTime() < deadline) {
                Thread.sleep(20)
            }
            processes.forEach { assertFalse(ProcessHandle.of(it).map(ProcessHandle::isAlive).orElse(false), "Process $it is still running") }
        }

        override fun close() {
            val processFile = directory.resolve("processes.txt")
            if (Files.exists(processFile)) {
                Files.readAllLines(processFile).forEach { pid -> ProcessHandle.of(pid.toLong()).ifPresent { it.destroyForcibly() } }
            }
            assertTrue(directory.toFile().deleteRecursively())
        }
    }

    companion object {
        private val fakeCodexSource = """
            import java.nio.charset.StandardCharsets;
            import java.nio.file.Files;
            import java.nio.file.Path;
            import java.util.Arrays;

            public class FakeCodex {
                public static void main(String[] args) throws Exception {
                    if (args.length == 1 && args[0].equals("child")) {
                        Thread.sleep(30000);
                        return;
                    }
                    Path directory = Path.of(System.getenv("FAKE_CODEX_DIRECTORY"));
                    Files.write(directory.resolve("arguments.txt"), Arrays.asList(args));
                    Files.writeString(directory.resolve("path.txt"), System.getenv("PATH"));
                    Path output = null;
                    for (int i = 0; i < args.length - 1; i++) {
                        if (args[i].equals("-o")) output = Path.of(args[i + 1]);
                    }
                    String mode = System.getenv("FAKE_CODEX_MODE");
                    if (mode.equals("wait")) {
                        String java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
                        Process child = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), "FakeCodex", "child").start();
                        Files.writeString(directory.resolve("processes.tmp"), ProcessHandle.current().pid() + "\n" + child.pid() + "\n");
                        Files.move(directory.resolve("processes.tmp"), directory.resolve("processes.txt"));
                        child.waitFor();
                        return;
                    }
                    if (mode.equals("nonzero")) {
                        System.err.println("sensitive-test-log");
                        System.exit(7);
                    }
                    if (mode.equals("usage-error")) {
                        System.err.println("error: unexpected argument '--ephemeral' found");
                        System.err.println("sensitive-test-log");
                        System.exit(2);
                    }
                    Files.writeString(directory.resolve("prompt.txt"), new String(System.in.readAllBytes(), StandardCharsets.UTF_8));
                    if (mode.equals("empty")) return;
                    if (mode.equals("large-output")) {
                        byte[] log = "sensitive-test-log".repeat(1024).getBytes(StandardCharsets.UTF_8);
                        for (int i = 0; i < 1000; i++) {
                            System.out.write(log);
                            System.err.write(log);
                        }
                    }
                    Files.writeString(output, "```text\nfix: generated message\n```\n");
                }
            }
        """.trimIndent()
    }
}
