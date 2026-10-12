package com.spc.goland.commitmessage

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.VcsDataKeys

class GenerateCommitMessageAction : AnAction("生成提交说明", "使用本地 Codex 根据当前变更生成提交说明", null) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.project != null && event.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL) != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val messageUi = event.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL) ?: return
        val files = CommitChangeCollector.collect(project, event.dataContext)
        if (files.isEmpty()) {
            Messages.showInfoMessage(project, "当前没有可提交的变更。", "生成提交说明")
            return
        }

        val models = CodexModels.load()
        val configured = CodexModels.configuredModel()
        val model = CommitMessageSettings.selectedModel()
            ?.takeIf { selected -> models.isEmpty() || models.any { it.slug == selected } }
            ?: configured?.takeIf { selected -> models.isEmpty() || models.any { it.slug == selected } }
            ?: models.firstOrNull()?.slug
        if (model.isNullOrBlank()) {
            Messages.showErrorDialog(project, "没有读取到本地 Codex 模型，请先确认 ~/.codex/config.toml。", "生成提交说明")
            return
        }
        CommitMessageSettings.selectModel(model)

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "正在生成提交说明", true) {
            private var generatedMessage = ""

            override fun run(indicator: ProgressIndicator) {
                indicator.text = model
                generatedMessage = CodexRunner.generate(project.basePath, model, CommitPrompt.build(CommitMessageSettings.prompt(), files))
            }

            override fun onSuccess() {
                messageUi.setCommitMessage(generatedMessage)
            }

            override fun onThrowable(error: Throwable) {
                Messages.showErrorDialog(project, error.message ?: "生成失败", "生成提交说明")
            }
        })
    }
}
