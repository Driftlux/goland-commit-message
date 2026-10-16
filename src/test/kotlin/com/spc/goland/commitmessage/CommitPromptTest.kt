package com.spc.goland.commitmessage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test
    fun preservesCustomPromptExactly() {
        val custom = "  only use this format\n\n"

        assertEquals(
            custom + "\n\n变更文件：\n对以下列表中未版本化的新增文件读取文件内容，不读取未列出的文件。\n- add src/new.kt",
            CommitPrompt.build(custom, listOf(FileChange("src/new.kt", FileKind.ADDED))),
        )
    }

    @Test
    fun includesEveryFileWhenSelectionExceedsEightyFiles() {
        val files = (1..100).map { FileChange("src/file$it.kt", FileKind.MODIFIED) }

        val prompt = CommitPrompt.build("custom instructions", files)

        assertEquals(100, prompt.lineSequence().count { it.startsWith("- update ") })
        assertTrue(prompt.contains("- update src/file81.kt"))
        assertTrue(prompt.endsWith("- update src/file100.kt"))
        assertFalse(prompt.contains("more files"))
    }
}
