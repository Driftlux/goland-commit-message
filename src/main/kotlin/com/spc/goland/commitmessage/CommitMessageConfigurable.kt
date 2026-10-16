package com.spc.goland.commitmessage

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.MutableProperty
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows

class CommitMessageConfigurable : BoundSearchableConfigurable(
    "提交说明生成",
    "com.spc.goland.commitmessage",
) {
    private val catalog = CodexModels.load()

    override fun createPanel(): DialogPanel {
        val options = (listOf("", currentModel()) + catalog.models.map { it.slug }).distinct()
        return panel {
            row("Codex 路径:") {
                textFieldWithBrowseButton(FileChooserDescriptorFactory.singleFile())
                    .bindText(CommitMessageSettings::executablePath, CommitMessageSettings::setExecutablePath)
                    .resizableColumn()
                    .align(AlignX.FILL)
                    .comment("可选择或输入 Codex 可执行文件的完整路径；留空时从 IDE 的 PATH 查找。")
            }
            row("模型:") {
                comboBox(options)
                    .applyToComponent { isEditable = true }
                    .bind(
                        { combo -> combo.editor.item?.toString().orEmpty().trim() },
                        { combo, value -> combo.selectedItem = value },
                        MutableProperty(::currentModel, ::saveModel),
                    )
                    .resizableColumn()
                    .align(AlignX.FILL)
                    .comment("可选择或输入模型名称；留空时沿用 Codex 配置，目录中的模型仅供参考。")
            }
            catalog.error?.let { error ->
                row {
                    text(error)
                }
            }
            row {
                text("本机运行的是 Codex CLI，不保证模型在本机。变更内容可能发送给 Codex 配置的模型服务；只读沙箱不等于离线推理。")
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
        CommitMessageSettings.selectModel(model.orEmpty())
    }

    private fun currentModel(): String {
        return CommitMessageSettings.selectedModel().orEmpty()
    }
}
