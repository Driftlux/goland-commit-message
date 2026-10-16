package com.spc.goland.commitmessage

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CodexModelsTest {
    @Test
    fun parsesFieldOrderWhitespaceAndEscapedNames() {
        val text = """
            {
              "models": [
                {"display_name": "Display \"name\"\nline", "slug": "provider/model"},
                {"slug": "model\\path", "display_name": "Unicode \u4e2d"}
              ]
            }
        """.trimIndent()

        assertEquals(
            listOf(
                CodexModel("provider/model", "Display \"name\"\nline"),
                CodexModel("model\\path", "Unicode \u4e2d"),
            ),
            CodexModels.parseCatalog(text),
        )
    }

    @Test
    fun usesSlugWhenDisplayNameIsMissing() {
        assertEquals(
            listOf(CodexModel("plain", "plain"), CodexModel("nullable", "nullable")),
            CodexModels.parseCatalog("""{"models":[{"slug":"plain"},{"slug":"nullable","display_name":null}]}"""),
        )
    }

    @Test
    fun rejectsInvalidJsonAndCatalogStructures() {
        listOf(
            "",
            "{models: []}",
            "{\"models\": []} trailing",
            "[]",
            "{}",
            """{"models":{}}""",
            """{"models":[null]}""",
            """{"models":[{}]}""",
            """{"models":[{"slug":1}]}""",
            """{"models":[{"slug":""}]}""",
            """{"models":[{"slug":"valid","display_name":1}]}""",
        ).forEach { text ->
            assertFailsWith<IllegalArgumentException> { CodexModels.parseCatalog(text) }
        }
    }

    @Test
    fun parsesOnlyTheTopLevelCatalogPath() {
        val text = """
            # model_catalog_json = "comment.json"
            model = "default-model"
            model_catalog_json = 'folder/custom#models.json' # trailing comment
            [profiles.work]
            model_catalog_json = "profile.json"
        """.trimIndent()

        assertEquals("folder/custom#models.json", CodexModels.catalogPathFromConfig(text))
        assertNull(CodexModels.catalogPathFromConfig("""
            # model_catalog_json = "comment.json"
            [profiles.work]
            model_catalog_json = "profile.json"
        """.trimIndent()))
    }

    @Test
    fun supportsQuotedKeysAndBasicStringEscapes() {
        assertEquals(
            "folder\\quoted\"name\u4e2d\ud83d\ude00.json",
            CodexModels.catalogPathFromConfig("""
                "model_catalog_json" = "folder\\quoted\"name\u4e2d\U0001F600.json"
            """.trimIndent()),
        )
        assertEquals(
            "C:\\models\\catalog.json",
            CodexModels.catalogPathFromConfig("'model_catalog_json' = 'C:\\models\\catalog.json'"),
        )
    }

    @Test
    fun ignoresCatalogTextInsideUnrelatedMultilineValues() {
        val text = "developer_instructions = \"\"\"\n" +
            "model_catalog_json = 'wrong.json'\n[not.a.table]\n\"\"\"\n" +
            "args = [\n  \"model_catalog_json = 'wrong.json'\",\n]\n" +
            "model_catalog_json = \"right.json\""

        assertEquals("right.json", CodexModels.catalogPathFromConfig(text))
    }

    @Test
    fun rejectsUnsupportedOrMalformedCatalogValues() {
        listOf(
            "model_catalog_json = 42",
            "model_catalog_json = \"\"",
            "model_catalog_json = \"\"\"multiline\"\"\"",
            "model_catalog_json = \"unfinished",
            "model_catalog_json = \"invalid\\q.json\"",
            "model_catalog_json = \"invalid\\uD800.json\"",
            "model_catalog_json = \"invalid\\u+041.json\"",
            "model_catalog_json = \"invalid\\U00110000.json\"",
            "model_catalog_json = \"path.json\" trailing",
            "developer_instructions = \"\"\"unfinished",
        ).forEach { text ->
            assertFailsWith<IllegalArgumentException> { CodexModels.catalogPathFromConfig(text) }
        }
    }

    @Test
    fun resolvesCodexHomeFromEnvironment() {
        val userHome = Path.of("user-home")
        assertEquals(Path.of("custom-home"), CodexModels.codexHome(mapOf("CODEX_HOME" to "custom-home"), userHome))
        assertEquals(userHome.resolve(".codex"), CodexModels.codexHome(emptyMap(), userHome))
    }

    @Test
    fun loadsConfiguredCatalogRelativeToCodexHome() = withTemporaryHome { home ->
        Files.writeString(home.resolve("config.toml"), "model_catalog_json = 'custom.json'")
        Files.writeString(home.resolve("custom.json"), """{"models":[{"slug":"configured"}]}""")
        Files.writeString(home.resolve("magpie-models.json"), """{"models":[{"slug":"fallback"}]}""")

        assertEquals(CodexModelCatalog(listOf(CodexModel("configured", "configured"))), CodexModels.load(home))
    }

    @Test
    fun loadsFallbackCatalogWhenNoCatalogIsConfigured() = withTemporaryHome { home ->
        Files.writeString(home.resolve("config.toml"), "model = \"cli-default\"\n[profiles.work]\nmodel_catalog_json = 'ignored.json'")
        Files.writeString(home.resolve("magpie-models.json"), """{"models":[{"slug":"fallback"}]}""")

        assertEquals(CodexModelCatalog(listOf(CodexModel("fallback", "fallback"))), CodexModels.load(home))
    }

    @Test
    fun loadsFallbackCatalogWithoutConfigFile() = withTemporaryHome { home ->
        Files.writeString(home.resolve("magpie-models.json"), """{"models":[{"slug":"fallback"}]}""")

        assertEquals(CodexModelCatalog(listOf(CodexModel("fallback", "fallback"))), CodexModels.load(home))
    }

    @Test
    fun loadsAbsoluteConfiguredCatalogPath() = withTemporaryHome { home ->
        val custom = home.resolve("custom.json").toAbsolutePath()
        Files.writeString(home.resolve("config.toml"), "model_catalog_json = '$custom'")
        Files.writeString(custom, """{"models":[{"slug":"absolute"}]}""")

        assertEquals(CodexModelCatalog(listOf(CodexModel("absolute", "absolute"))), CodexModels.load(home))
    }

    @Test
    fun missingOptionalFilesReturnNoSuggestions() = withTemporaryHome { home ->
        assertEquals(CodexModelCatalog(), CodexModels.load(home))
    }

    @Test
    fun missingConfiguredCatalogReportsErrorWithoutUsingFallback() = withTemporaryHome { home ->
        Files.writeString(home.resolve("config.toml"), "model_catalog_json = 'missing.json'")
        Files.writeString(home.resolve("magpie-models.json"), """{"models":[{"slug":"fallback"}]}""")

        val result = CodexModels.load(home)
        assertTrue(result.models.isEmpty())
        assertNotNull(result.error)
        assertTrue(result.error.contains("missing.json"))
    }

    @Test
    fun malformedConfigReportsErrorWithoutUsingFallback() = withTemporaryHome { home ->
        Files.writeString(home.resolve("config.toml"), "model_catalog_json = \"invalid\\q-secret.json\"")
        Files.writeString(home.resolve("magpie-models.json"), """{"models":[{"slug":"fallback"}]}""")

        val result = CodexModels.load(home)
        assertTrue(result.models.isEmpty())
        assertNotNull(result.error)
        assertTrue(result.error.contains("config.toml"))
        assertTrue(!result.error.contains("secret"))
    }

    @Test
    fun malformedCatalogReportsErrorWithoutItsContents() = withTemporaryHome { home ->
        Files.writeString(home.resolve("magpie-models.json"), "{secret-value}")

        val result = CodexModels.load(home)
        assertTrue(result.models.isEmpty())
        assertNotNull(result.error)
        assertTrue(!result.error.contains("secret-value"))
    }

    @Test
    fun unreadableCatalogAndConfigDirectoriesReportErrors() = withTemporaryHome { home ->
        Files.createDirectory(home.resolve("magpie-models.json"))
        assertNotNull(CodexModels.load(home).error)
        Files.createDirectory(home.resolve("config.toml"))
        assertNotNull(CodexModels.load(home).error)
    }

    private fun withTemporaryHome(test: (Path) -> Unit) {
        val home = Files.createTempDirectory("codex-models-test-")
        try {
            test(home)
        } finally {
            Files.walk(home).use { files ->
                files.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) }
            }
        }
    }
}
