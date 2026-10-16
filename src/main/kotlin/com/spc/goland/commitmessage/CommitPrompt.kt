package com.spc.goland.commitmessage

internal object CommitPrompt {
    val defaultPrompt = """
        你是 Git 提交说明生成器。先查看列出的文件的实际 diff，不要只根据文件名猜测。未列出的文件不要写入说明。

        只返回提交说明本身，不要解释、标题或代码块。

        格式：
        type(scope): 中文摘要

        正文只写两类内容：
        - 新增了什么功能
        - 改动了什么行为

        要求：
        - 面向没做过这个项目的人，使用业务语言说明变更。
        - 不要罗列代码中的函数名、类型名、结构体名或内部标识符。
        - 使用 Conventional Commits。type 从 feat、fix、perf、refactor、docs、test、chore 中选择。
        - scope 使用业务模块；无法判断时省略括号。
        - 摘要用一句话说明新增或改动的内容。
        - 正文用中文，只保留用户能理解的配置项和关键数值。
        - 不写原因分析、文件清单或 Markdown。
    """.trimIndent()

    fun build(prompt: String, files: List<FileChange>): String {
        val listed = files.joinToString("\n") { "- ${it.kind.label} ${it.path}" }
        return prompt + "\n\n变更文件：\n对以下列表中未版本化的新增文件读取文件内容，不读取未列出的文件。\n" + listed
    }
}
