package com.spc.goland.commitmessage

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.VcsDataKeys
import git4idea.index.ui.GitStageCommitPanel

class GenerateCommitMessageAction : AnAction("生成提交说明", "通过本机 Codex CLI 根据勾选变更生成提交说明", null) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.project != null &&
            event.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL) != null &&
            event.getData(VcsDataKeys.COMMIT_MESSAGE_DOCUMENT) != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val messageUi = event.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL) ?: return
        val messageDocument = event.getData(VcsDataKeys.COMMIT_MESSAGE_DOCUMENT) ?: return
        if (event.getData(VcsDataKeys.COMMIT_WORKFLOW_UI) is GitStageCommitPanel) {
            Messages.showInfoMessage(project, "暂不支持暂存区提交，请使用普通提交面板并选择文件的全部变更后生成提交说明。", "生成提交说明")
            return
        }
        if (CommitChangeCollector.hasPartialChanges(project, event.dataContext)) {
            Messages.showInfoMessage(project, "暂不支持部分行或部分变更提交，请选择文件的全部变更后生成提交说明。", "生成提交说明")
            return
        }
        val files = CommitChangeCollector.collect(project, event.dataContext)
        if (files.isEmpty()) {
            Messages.showInfoMessage(project, "当前没有勾选可提交的变更。", "生成提交说明")
            return
        }

        val model = CommitMessageSettings.selectedModel()
        val executablePath = CommitMessageSettings.executablePath()
        val prompt = CommitPrompt.build(CommitMessageSettings.prompt(), files)
        val originalStamp = messageDocument.modificationStamp

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "正在生成提交说明", true) {
            private var generatedMessage = ""

            override fun run(indicator: ProgressIndicator) {
                indicator.text = model ?: "沿用 Codex 配置"
                indicator.checkCanceled()
                generatedMessage = CodexRunner.generate(
                    project.basePath,
                    model,
                    prompt,
                    executablePath,
                    indicator::checkCanceled,
                )
                indicator.checkCanceled()
            }

            override fun onSuccess() {
                if (project.isDisposed) {
                    return
                }
                if (messageDocument.modificationStamp != originalStamp) {
                    Messages.showInfoMessage(project, "生成期间提交说明已被修改，本次结果未覆盖你的输入。", "生成提交说明")
                    return
                }
                messageUi.setCommitMessage(generatedMessage)
            }

            override fun onThrowable(error: Throwable) {
                if (project.isDisposed) {
                    return
                }
                Messages.showErrorDialog(project, error.message ?: "生成失败", "生成提交说明")
            }
        })
    }
}
