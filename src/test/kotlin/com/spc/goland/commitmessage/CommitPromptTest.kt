package com.spc.goland.commitmessage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommitPromptTest {
    @Test
    fun appendsChangedFilesAfterCustomPrompt() {
        val prompt = CommitPrompt.build(
            "只返回一行提交说明",
            listOf(
                FileChange("internal/entry/handler.go", FileKind.ADDED),
                FileChange("internal/entry/types.go", FileKind.MODIFIED),
            ),
        )

        assertTrue(prompt.startsWith("只返回一行提交说明"))
        assertTrue(prompt.contains("- add internal/entry/handler.go"))
        assertTrue(prompt.contains("- update internal/entry/types.go"))
    }

    @Test
    fun keepsProjectRelativePath() {
        assertEquals(
            "internal/entry/types.go",
            toRelativePath("/work/billing/internal/entry/types.go", "/work/billing"),
        )
    }
}
