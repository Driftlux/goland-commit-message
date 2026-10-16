package com.spc.goland.commitmessage

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.Strictness
import com.intellij.util.EnvironmentUtil
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.NoSuchFileException
import java.nio.file.Path

internal data class CodexModel(
    val slug: String,
    val displayName: String,
)

internal data class CodexModelCatalog(
    val models: List<CodexModel> = emptyList(),
    val error: String? = null,
)

internal object CodexModels {
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()

    fun load(): CodexModelCatalog = try {
        load(codexHome())
    } catch (_: InvalidPathException) {
        CodexModelCatalog(error = "CODEX_HOME 不是有效目录路径")
    }

    fun load(codexHome: Path): CodexModelCatalog {
        var source = codexHome.resolve("config.toml")
        return try {
            val configured = try {
                catalogPathFromConfig(Files.readString(source))
            } catch (_: NoSuchFileException) {
                null
            }
            source = configured?.let { codexHome.resolve(Path.of(it)) }
                ?: codexHome.resolve("magpie-models.json")
            val text = try {
                Files.readString(source)
            } catch (_: NoSuchFileException) {
                return if (configured == null) CodexModelCatalog()
                else CodexModelCatalog(error = "指定的模型目录不存在：$source")
            }
            CodexModelCatalog(models = parseCatalog(text))
        } catch (_: IOException) {
            CodexModelCatalog(error = "无法读取模型目录配置：$source")
        } catch (_: SecurityException) {
            CodexModelCatalog(error = "没有权限读取模型目录配置：$source")
        } catch (_: InvalidPathException) {
            CodexModelCatalog(error = "模型目录路径无效（$source）")
        } catch (error: IllegalArgumentException) {
            CodexModelCatalog(error = "模型目录配置错误（$source）：${error.message}")
        }
    }

    internal fun codexHome(
        environment: Map<String, String> = EnvironmentUtil.getEnvironmentMap(),
        userHome: Path = Path.of(System.getProperty("user.home")),
    ): Path = environment["CODEX_HOME"]?.takeIf { it.isNotBlank() }?.let(Path::of)
        ?: userHome.resolve(".codex")

    internal fun parseCatalog(text: String): List<CodexModel> {
        val root = try {
            gson.fromJson(text, JsonElement::class.java)
        } catch (_: JsonParseException) {
            throw IllegalArgumentException("模型目录不是有效 JSON")
        }
        require(root != null && root.isJsonObject) { "模型目录必须是 JSON 对象" }
        val models = root.asJsonObject["models"]
        require(models != null && models.isJsonArray) { "模型目录必须包含 models 数组" }
        return models.asJsonArray.map { item ->
            require(item.isJsonObject) { "models 中的模型必须是 JSON 对象" }
            val model = item.asJsonObject
            val slug = stringField(model["slug"], "slug")
            require(slug.isNotBlank()) { "模型的 slug 不能为空" }
            val displayName = model["display_name"]?.takeUnless { it.isJsonNull }
                ?.let { stringField(it, "display_name") }?.ifBlank { slug } ?: slug
            CodexModel(slug, displayName)
        }.distinctBy { it.slug }
    }

    private fun stringField(value: JsonElement?, name: String): String {
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString) {
            "模型的 $name 必须是字符串"
        }
        return value.asString
    }

    internal fun catalogPathFromConfig(text: String): String? {
        var quote: Char? = null
        var multiline = false
        var nesting = 0
        for (line in text.lineSequence()) {
            if (quote == null && nesting == 0) {
                val statement = line.trimStart()
                if (statement.isEmpty() || statement.startsWith('#')) continue
                if (statement.startsWith('[')) return null
                val equals = assignmentIndex(statement)
                require(equals > 0) { "无法安全读取顶层 model_catalog_json 配置" }
                val rawKey = statement.substring(0, equals).trim()
                val key = if (rawKey.startsWith('"') || rawKey.startsWith('\'')) {
                    val (value, end) = quotedString(rawKey)
                    value.takeIf { end == rawKey.length }
                } else rawKey
                if (key == "model_catalog_json") {
                    val valueText = statement.substring(equals + 1).trimStart()
                    val (value, end) = quotedString(valueText)
                    val remaining = valueText.substring(end).trimStart()
                    require(remaining.isEmpty() || remaining.startsWith('#')) {
                        "model_catalog_json 字符串后有不支持的内容"
                    }
                    require(value.isNotBlank()) { "model_catalog_json 不能为空" }
                    return value
                }
            }

            // Track unrelated multiline values so their contents cannot be mistaken for a top-level key.
            var index = 0
            while (index < line.length) {
                val char = line[index]
                if (quote != null) {
                    if (quote == '"' && char == '\\') {
                        index += 2
                        continue
                    }
                    if (char == quote && (!multiline || line.startsWith("$char$char$char", index))) {
                        if (multiline) {
                            while (index < line.length && line[index] == char) index++
                        } else index++
                        quote = null
                        multiline = false
                        continue
                    }
                } else when (char) {
                    '#' -> break
                    '"', '\'' -> {
                        quote = char
                        multiline = line.startsWith("$char$char$char", index)
                        index += if (multiline) 3 else 1
                        continue
                    }
                    '[', '{' -> nesting++
                    ']', '}' -> {
                        nesting--
                        require(nesting >= 0) { "无法安全读取顶层 model_catalog_json 配置" }
                    }
                }
                index++
            }
            require(quote == null || multiline) { "配置包含未闭合的单行字符串" }
        }
        require(quote == null && nesting == 0) { "配置包含未闭合的多行内容" }
        return null
    }

    private fun assignmentIndex(text: String): Int {
        var index = 0
        while (index < text.length) {
            when (text[index]) {
                '"', '\'' -> index += quotedString(text.substring(index)).second
                '=' -> return index
                '#' -> return -1
                else -> index++
            }
        }
        return -1
    }

    private fun quotedString(text: String): Pair<String, Int> {
        val quote = text.firstOrNull()
        require(quote == '"' || quote == '\'') { "model_catalog_json 必须是单行字符串" }
        require(!text.startsWith("$quote$quote$quote")) { "model_catalog_json 不支持多行字符串" }
        val value = StringBuilder()
        var index = 1
        while (index < text.length) {
            val char = text[index++]
            if (char == quote) return value.toString() to index
            require((char >= ' ' && char != '\u007F') || char == '\t') { "model_catalog_json 包含无效控制字符" }
            if (quote == '\'' || char != '\\') {
                value.append(char)
                continue
            }
            require(index < text.length) { "model_catalog_json 包含未完成的转义" }
            when (val escape = text[index++]) {
                'b' -> value.append('\b')
                't' -> value.append('\t')
                'n' -> value.append('\n')
                'f' -> value.append('\u000C')
                'r' -> value.append('\r')
                '"', '\\' -> value.append(escape)
                'u', 'U' -> {
                    val end = index + if (escape == 'u') 4 else 8
                    val codePoint = text.substring(index, minOf(end, text.length))
                        .takeIf { digits ->
                            digits.length == end - index && digits.all { it.digitToIntOrNull(16) != null }
                        }?.toIntOrNull(16)
                    require(codePoint != null && Character.isValidCodePoint(codePoint) && codePoint !in 0xD800..0xDFFF) {
                        "model_catalog_json 包含无效 Unicode 转义"
                    }
                    value.appendCodePoint(codePoint)
                    index = end
                }
                else -> throw IllegalArgumentException("model_catalog_json 包含不支持的转义")
            }
        }
        throw IllegalArgumentException("model_catalog_json 字符串未闭合")
    }
}
