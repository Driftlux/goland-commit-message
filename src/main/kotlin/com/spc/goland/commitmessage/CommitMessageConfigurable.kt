package com.spc.goland.commitmessage

import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows

class CommitMessageConfigurable : BoundSearchableConfigurable(
    "提交说明生成",
    "com.spc.goland.commitmessage",
) {
    private val models = CodexModels.load()

    override fun createPanel(): DialogPanel {
        val options = models.map { it.slug }.ifEmpty { listOf(currentModel()) }.distinct()
        return panel {
            row("模型:") {
                comboBox(options)
                    .bindItem(::currentModel, ::saveModel)
                    .comment("使用本地 Codex 模型目录中的模型。")
            }
            row {
                label("全局提示词")
            }
            row {
                textArea()
                    .rows(14)
                    .bindText(CommitMessageSettings::prompt, CommitMessageSettings::setPrompt)
                    .comment("生成提交说明时使用。变更文件列表会自动追加到提示词后面。")
                    .resizableColumn()
                    .align(AlignX.FILL)
            }
        }
    }

    private fun saveModel(model: String?) {
        if (!model.isNullOrBlank()) {
            CommitMessageSettings.selectModel(model)
        }
    }

    private fun currentModel(): String {
        return CommitMessageSettings.selectedModel()
            ?.takeIf { selected -> models.isEmpty() || models.any { it.slug == selected } }
            ?: CodexModels.configuredModel()
            ?: models.firstOrNull()?.slug
            ?: ""
    }
}
