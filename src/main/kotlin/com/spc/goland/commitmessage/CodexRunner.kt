package com.spc.goland.commitmessage

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.EnvironmentUtil
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

internal object CodexRunner {
    private const val timeoutMillis = 180_000L
    private const val maxErrorOutputBytes = 16 * 1024

    fun generate(
        projectPath: String?,
        model: String?,
        prompt: String,
        executablePath: String? = null,
        checkCanceled: () -> Unit = {},
    ): String = generate(
        projectPath, model, prompt, executablePath, checkCanceled,
        timeoutMillis, null, EnvironmentUtil.getEnvironmentMap(),
    )

    internal fun generate(
        projectPath: String?,
        model: String?,
        prompt: String,
        executablePath: String?,
        checkCanceled: () -> Unit,
        timeoutMillis: Long,
        tempDirectory: Path?,
        environment: Map<String, String>,
    ): String {
        checkCanceled()
        val executable = findCodex(executablePath, environment)
        val temporaryFiles = mutableListOf<Path>()
        var process: Process? = null
        var failure: Throwable? = null
        try {
            fun temporaryFile(suffix: String): Path {
                val file = if (tempDirectory == null) {
                    Files.createTempFile("codex-commit-message-", suffix)
                } else {
                    Files.createTempFile(tempDirectory, "codex-commit-message-", suffix)
                }
                temporaryFiles.add(file)
                return file
            }

            val output = temporaryFile(".txt")
            val input = temporaryFile(".prompt")
            val errorOutput = temporaryFile(".err")
            Files.writeString(input, prompt, StandardCharsets.UTF_8)
            checkCanceled()
            // File-backed stdin cannot block waiting for Codex to read a large prompt.
            val runningProcess = commandLine(executable, projectPath, model, output, environment)
                .toProcessBuilder()
                .redirectInput(input.toFile())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(errorOutput.toFile())
                .start()
            process = runningProcess

            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
            while (true) {
                checkCanceled()
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0) {
                    error("Codex 生成超时")
                }
                if (runningProcess.waitFor(minOf(remaining, TimeUnit.MILLISECONDS.toNanos(100)), TimeUnit.NANOSECONDS)) {
                    break
                }
            }
            checkCanceled()
            val exitCode = runningProcess.exitValue()
            if (exitCode != 0) {
                error(failureMessage(exitCode, readErrorOutput(errorOutput)))
            }

            val message = Files.readString(output, StandardCharsets.UTF_8)
                .trim()
                .removePrefix("```text")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            if (message.isBlank()) {
                error("Codex 没有返回提交说明")
            }
            return message
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            var cleanupFailure: Throwable? = null
            fun cleanUp(action: () -> Unit) {
                try {
                    action()
                } catch (error: Throwable) {
                    if (cleanupFailure == null) cleanupFailure = error else cleanupFailure.addSuppressed(error)
                }
            }
            cleanUp { process?.takeIf { it.isAlive }?.let(::stopProcess) }
            temporaryFiles.forEach { file -> cleanUp { Files.deleteIfExists(file) } }
            cleanupFailure?.let { error ->
                val originalFailure = failure
                if (originalFailure == null) throw error else originalFailure.addSuppressed(error)
            }
        }
    }

    internal fun findCodex(executablePath: String?, environment: Map<String, String>): Path {
        if (!executablePath.isNullOrBlank()) {
            val executable = Path.of(executablePath.trim()).toAbsolutePath().normalize()
            if (!Files.isRegularFile(executable) || !Files.isExecutable(executable)) {
                error("配置的 Codex 路径不存在或不可执行，请检查 Codex 可执行文件路径。")
            }
            return executable
        }

        val path = environment.entries.firstOrNull { it.key.equals("PATH", ignoreCase = SystemInfoRt.isWindows) }?.value.orEmpty()
        val executable = if (SystemInfoRt.isWindows) {
            PathEnvironmentVariableUtil.findExecutableInWindowsPath("codex", path)?.let(Path::of)
        } else {
            PathEnvironmentVariableUtil.findInPath("codex", path) { it.isFile && it.canExecute() }?.toPath()
        }
        return executable?.toAbsolutePath()?.normalize()
            ?: error("没有在 PATH 中找到 Codex，请确认已安装，或在设置中指定 Codex 可执行文件路径。")
    }

    // 只按关键字归类常见失败原因，不回显 CLI 日志，避免把敏感内容带到提示框里。
    internal fun failureMessage(exitCode: Int, errorOutput: String): String {
        val text = errorOutput.lowercase()
        val authenticationFailure = "not logged in" in text ||
            "unauthorized" in text ||
            "invalid api key" in text ||
            "401 unauthorized" in text
        val hint = when {
            "unexpected argument" in text || "unrecognized argument" in text || "unknown argument" in text ->
                "Codex CLI 不支持当前调用参数，可能是版本过旧，请升级 Codex 后重试。"
            authenticationFailure ->
                "Codex 未登录或认证已失效，请先在终端完成 codex login。"
            "unknown model" in text || "model not found" in text ->
                "Codex 无法使用所选模型，请检查模型名称，或留空以沿用 Codex 配置。"
            exitCode == 2 ->
                "Codex CLI 拒绝了调用参数，可能是版本过旧，请升级 Codex 后重试。"
            else -> "请检查 Codex 认证和模型配置。"
        }
        return "Codex 生成失败（退出码 $exitCode）。$hint"
    }

    private fun readErrorOutput(path: Path): String = try {
        Files.newInputStream(path).use { input ->
            String(input.readNBytes(maxErrorOutputBytes), StandardCharsets.UTF_8)
        }
    } catch (_: IOException) {
        ""
    }

    internal fun commandLine(
        executable: Path,
        projectPath: String?,
        model: String?,
        output: Path,
        environment: Map<String, String>,
        windows: Boolean = SystemInfoRt.isWindows,
    ): GeneralCommandLine {
        val command = GeneralCommandLine(executable.toString())
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.NONE)
            .withEnvironment(environment)
            .withCharset(StandardCharsets.UTF_8)
            .withParameters("exec", "--ephemeral", "--skip-git-repo-check", "--sandbox", "read-only", "--color", "never")
        if (!projectPath.isNullOrBlank()) command.addParameters("--cd", projectPath)
        if (!model.isNullOrBlank()) command.addParameters("-m", model)
        command.addParameters("-o", output.toString(), "-")

        val pathKey = command.environment.keys.firstOrNull { it.equals("PATH", ignoreCase = windows) } ?: "PATH"
        val path = command.environment[pathKey].orEmpty()
        val binDirectory = executable.parent.toString()
        if (path.split(File.pathSeparatorChar).none { it.equals(binDirectory, ignoreCase = windows) }) {
            command.environment[pathKey] = if (path.isBlank()) binDirectory else "$binDirectory${File.pathSeparator}$path"
        }
        if (windows && executable.fileName.toString().let { it.endsWith(".cmd", true) || it.endsWith(".bat", true) }) {
            command.withWrappingCommand("cmd.exe", "/d", "/c")
        }
        return command
    }

    private fun stopProcess(process: Process) {
        val descendants = process.descendants().use { it.toList() }
        descendants.asReversed().forEach { it.destroyForcibly() }
        process.destroyForcibly()
        check(process.waitFor(5, TimeUnit.SECONDS)) { "无法停止 Codex 进程" }
    }
}
