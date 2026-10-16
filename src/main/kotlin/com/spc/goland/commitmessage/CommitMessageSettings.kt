package com.spc.goland.commitmessage

import com.intellij.ide.util.PropertiesComponent

internal object CommitMessageSettings {
    private const val modelKey = "com.spc.goland.commitmessage.model"
    private const val promptKey = "com.spc.goland.commitmessage.prompt"
    private const val executableKey = "com.spc.goland.commitmessage.executable"

    fun selectedModel(): String? {
        return PropertiesComponent.getInstance().getValue(modelKey)?.takeIf { it.isNotBlank() }
    }

    fun selectModel(model: String) {
        PropertiesComponent.getInstance().setValue(modelKey, model.trim(), "")
    }

    fun executablePath(): String {
        return PropertiesComponent.getInstance().getValue(executableKey, "")
    }

    fun setExecutablePath(path: String) {
        PropertiesComponent.getInstance().setValue(executableKey, path.trim(), "")
    }

    fun prompt(): String {
        return PropertiesComponent.getInstance().getValue(promptKey, CommitPrompt.defaultPrompt)
    }

    fun setPrompt(prompt: String) {
        val value = prompt.trim().ifBlank { CommitPrompt.defaultPrompt }
        PropertiesComponent.getInstance().setValue(promptKey, value)
    }
}
