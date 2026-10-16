package com.spc.goland.commitmessage

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.impl.PartialChangesUtil

internal object CommitChangeCollector {
    fun collect(project: Project, dataContext: DataContext): List<FileChange> {
        val base = project.basePath
        val changes = includedChanges(dataContext).mapNotNull { it.toFileChange(base) }
        val unversionedFiles = dataContext.getData(VcsDataKeys.COMMIT_WORKFLOW_UI)
            ?.getIncludedUnversionedFiles().orEmpty()
        return changes + unversionedFiles.map { FileChange(toRelativePath(it.path, base), FileKind.ADDED) }
    }

    fun hasPartialChanges(project: Project, dataContext: DataContext): Boolean {
        return includedChanges(dataContext).any { change ->
            PartialChangesUtil.isPartialChange(change) ||
                PartialChangesUtil.getPartialTracker(project, change)?.hasPartialChangesToCommit() == true
        }
    }

    private fun includedChanges(dataContext: DataContext): List<Change> {
        val workflow = dataContext.getData(VcsDataKeys.COMMIT_WORKFLOW_UI)
        if (workflow != null) {
            return workflow.getIncludedChanges()
        }

        return dataContext.getData(VcsDataKeys.CHANGES)?.toList().orEmpty()
    }
}

internal fun Change.toFileChange(basePath: String?): FileChange? {
    val revision = afterRevision ?: beforeRevision ?: return null
    return FileChange(toRelativePath(revision.file.path, basePath), kindOf(this))
}

internal fun toRelativePath(fullPath: String, basePath: String?): String {
    val normalized = fullPath.replace('\\', '/').trimEnd('/')
    val base = basePath?.replace('\\', '/')?.trimEnd('/')
    val relative = when {
        base.isNullOrBlank() -> normalized
        normalized == base -> normalized.substringAfterLast('/')
        normalized.startsWith("$base/") -> normalized.removePrefix("$base/")
        else -> normalized
    }
    return relative.trimStart('/')
}

internal fun kindOf(change: Change): FileKind {
    val before = change.beforeRevision
    val after = change.afterRevision
    return when {
        before == null && after != null -> FileKind.ADDED
        before != null && after == null -> FileKind.DELETED
        change.isRenamed || change.isMoved -> FileKind.RENAMED
        else -> FileKind.MODIFIED
    }
}

internal data class FileChange(
    val path: String,
    val kind: FileKind,
)

internal enum class FileKind(val label: String) {
    ADDED("add"),
    DELETED("remove"),
    MODIFIED("update"),
    RENAMED("rename"),
}
