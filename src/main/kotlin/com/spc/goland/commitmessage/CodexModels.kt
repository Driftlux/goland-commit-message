package com.spc.goland.commitmessage

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

internal data class CodexModel(
    val slug: String,
    val displayName: String,
)

internal object CodexModels {
    fun configuredModel(): String? {
        val config = Path.of(System.getProperty("user.home"), ".codex", "config.toml")
        if (!config.exists()) {
            return null
        }
        return Regex("""(?m)^model\s*=\s*"([^"]+)"""")
            .find(config.readText())
            ?.groupValues
            ?.get(1)
    }

    fun load(): List<CodexModel> {
        val path = catalogPath() ?: return emptyList()
        val text = path.readText()
        val models = Regex(""""slug"\s*:\s*"([^"]+)".*?"display_name"\s*:\s*"([^"]+)"""")
            .findAll(text)
            .map { CodexModel(it.groupValues[1], it.groupValues[2]) }
            .distinctBy { it.slug }
            .toList()
        return models.ifEmpty { fallbackModels(text) }
    }

    private fun catalogPath(): Path? {
        val home = Path.of(System.getProperty("user.home"), ".codex")
        val config = home.resolve("config.toml")
        if (config.exists()) {
            val match = Regex("""model_catalog_json\s*=\s*"([^"]+)"""")
                .find(config.readText())
            val configured = match?.groupValues?.get(1)?.let(Path::of)
            if (configured != null && configured.exists()) {
                return configured
            }
        }
        val fallback = home.resolve("magpie-models.json")
        return fallback.takeIf { it.exists() }
    }

    private fun fallbackModels(text: String): List<CodexModel> {
        return Regex(""""slug"\s*:\s*"([^"]+)"""")
            .findAll(text)
            .map { CodexModel(it.groupValues[1], it.groupValues[1]) }
            .distinctBy { it.slug }
            .toList()
    }
}
