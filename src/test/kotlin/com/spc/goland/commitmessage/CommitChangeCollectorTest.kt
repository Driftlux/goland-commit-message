package com.spc.goland.commitmessage

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.vcs.commit.CommitWorkflowUi
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CommitChangeCollectorTest {
    private val project = proxy<Project> { method ->
        when (method) {
            "getBasePath" -> "/work/project"
            else -> error("Unexpected project call: $method")
        }
    }

    @Test
    fun emptyWorkflowSelectionDoesNotFallBackToContextChanges() {
        val context = context(workflow(), arrayOf(addedChange("unselected.kt")))

        assertEquals(emptyList(), CommitChangeCollector.collect(project, context))
        assertFalse(CommitChangeCollector.hasPartialChanges(project, context))
    }

    @Test
    fun missingCommitContextReturnsNoFiles() {
        assertEquals(emptyList(), CommitChangeCollector.collect(project, context()))
        assertFalse(CommitChangeCollector.hasPartialChanges(project, context()))
    }

    @Test
    fun explicitChangesAreUsedWhenWorkflowIsMissing() {
        val context = context(changes = arrayOf(addedChange("src/selected.kt")))

        assertEquals(
            listOf(FileChange("src/selected.kt", FileKind.ADDED)),
            CommitChangeCollector.collect(project, context),
        )
    }

    @Test
    fun workflowSelectionTakesPrecedenceOverContextChanges() {
        val context = context(
            workflow(listOf(addedChange("src/selected.kt"))),
            arrayOf(addedChange("src/unselected.kt")),
        )

        assertEquals(
            listOf(FileChange("src/selected.kt", FileKind.ADDED)),
            CommitChangeCollector.collect(project, context),
        )
    }

    @Test
    fun includesCheckedUnversionedFilesAlongsideTrackedChanges() {
        val context = context(
            workflow(
                listOf(addedChange("src/tracked.kt")),
                listOf(unversionedFile("src/new.kt")),
            ),
        )

        assertEquals(
            listOf(
                FileChange("src/tracked.kt", FileKind.ADDED),
                FileChange("src/new.kt", FileKind.ADDED),
            ),
            CommitChangeCollector.collect(project, context),
        )
    }

    @Test
    fun selectionCanContainOnlyUnversionedFiles() {
        val context = context(workflow(unversioned = listOf(unversionedFile("src/new.kt"))))

        assertEquals(
            listOf(FileChange("src/new.kt", FileKind.ADDED)),
            CommitChangeCollector.collect(project, context),
        )
    }

    @Test
    fun keepsAllFilesInLargeWorkflowSelection() {
        val changes = (1..100).map { addedChange("src/file$it.kt") }

        val files = CommitChangeCollector.collect(project, context(workflow(changes)))

        assertEquals(100, files.size)
        assertEquals(FileChange("src/file81.kt", FileKind.ADDED), files[80])
        assertEquals(FileChange("src/file100.kt", FileKind.ADDED), files.last())
    }

    private fun context(
        workflow: CommitWorkflowUi? = null,
        changes: Array<Change>? = null,
    ): DataContext = DataContext { dataId ->
        when (dataId) {
            VcsDataKeys.COMMIT_WORKFLOW_UI.name -> workflow
            VcsDataKeys.CHANGES.name -> changes
            else -> null
        }
    }

    private fun workflow(
        changes: List<Change> = emptyList(),
        unversioned: List<FilePath> = emptyList(),
    ): CommitWorkflowUi = proxy { method ->
        when (method) {
            "getIncludedChanges" -> changes
            "getIncludedUnversionedFiles" -> unversioned
            else -> error("Unexpected workflow call: $method")
        }
    }

    private fun addedChange(relativePath: String): Change {
        val path = "/work/project/$relativePath"
        val filePath = proxy<FilePath> { method ->
            when (method) {
                "getPath" -> path
                "isDirectory", "isNonLocal" -> false
                else -> error("Unexpected file path call: $method")
            }
        }
        val revision = proxy<ContentRevision> { method ->
            when (method) {
                "getFile" -> filePath
                else -> error("Unexpected revision call: $method")
            }
        }
        return Change(null, revision)
    }

    private fun unversionedFile(relativePath: String): FilePath = proxy { method ->
        when (method) {
            "getPath" -> "/work/project/$relativePath"
            else -> error("Unexpected unversioned file path call: $method")
        }
    }

    private inline fun <reified T> proxy(crossinline answer: (String) -> Any?): T {
        return Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { instance, method, args ->
            when (method.name) {
                "equals" -> instance === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(instance)
                "toString" -> "Test ${T::class.java.simpleName}"
                else -> answer(method.name)
            }
        } as T
    }
}
