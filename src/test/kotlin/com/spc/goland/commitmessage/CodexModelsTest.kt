package com.spc.goland.commitmessage

import kotlin.test.Test
import kotlin.test.assertEquals

class CodexModelsTest {
    @Test
    fun readsSlugAndDisplayNameFromCatalogText() {
        val text = """
            {"models":[{"slug":"xtj/grok-4.7","display_name":"Grok 4.7 · 新途径AI中台"}]}
        """.trimIndent()
        val models = Regex(""""slug"\s*:\s*"([^"]+)".*?"display_name"\s*:\s*"([^"]+)"""")
            .findAll(text)
            .map { CodexModel(it.groupValues[1], it.groupValues[2]) }
            .toList()

        assertEquals(listOf(CodexModel("xtj/grok-4.7", "Grok 4.7 · 新途径AI中台")), models)
    }
}
