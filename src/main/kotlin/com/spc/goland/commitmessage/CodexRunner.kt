package com.spc.goland.commitmessage

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.io.path.isExecutable

internal object CodexRunner {
    private const val timeoutMinutes = 3L

    fun generate(projectPath: String?, model: String, prompt: String): String {
        val executable = findCodex()
        val output = Files.createTempFile("codex-commit-message-", ".txt")
        val command = mutableListOf(
            executable.toString(),
            "exec",
            "--ephemeral",
            "--skip-git-repo-check",
            "--sandbox",
            "read-only",
            "--color",
            "never",
            "-m",
            model,
            "-o",
            output.toString(),
            "-",
        )
        if (!projectPath.isNullOrBlank()) {
            command.add(4, projectPath)
            command.add(4, "--cd")
        }

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .also { builder -> prependPath(builder, executable.parent) }
            .start()
        process.outputStream.bufferedWriter().use { it.write(prompt) }
        val finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES)
        val log = process.inputStream.bufferedReader().readText()
        if (!finished) {
            process.destroyForcibly()
            error("Codex 生成超时")
        }
        if (process.exitValue() != 0) {
            error(log.lineSequence().lastOrNull { it.isNotBlank() } ?: "Codex 生成失败")
        }

        val message = Files.readString(output)
            .trim()
            .removePrefix("```text")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        if (message.isBlank()) {
            error("Codex 没有返回提交说明")
        }
        return message
    }

    private fun prependPath(builder: ProcessBuilder, binDir: Path?) {
        if (binDir == null || !binDir.exists()) {
            return
        }
        val env = builder.environment()
        val current = env["PATH"].orEmpty()
        val prefix = binDir.toString()
        if (current.split(':').none { it == prefix }) {
            env["PATH"] = if (current.isBlank()) prefix else "$prefix:$current"
        }
    }

    private fun findCodex(): Path {
        val home = Path.of(System.getProperty("user.home"))
        val candidates = listOf(
            home.resolve(".nvm/versions/node/v26.9.0/bin/codex"),
            home.resolve(".local/bin/codex"),
            Path.of("/opt/homebrew/bin/codex"),
            Path.of("/usr/local/bin/codex"),
        )
        return candidates.firstOrNull { it.exists() && it.isExecutable() }
            ?: error("没有找到本地 Codex，请确认 codex 已安装")
    }
}
