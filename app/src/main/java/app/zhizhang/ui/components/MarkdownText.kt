package app.zhizhang.ui.components

/**
 * GitHub Release 的正文是 Markdown。更新弹窗不引入 Markdown 渲染器，
 * 先转成干净的纯文本，避免把 #、**、[]() 等标记直接展示给用户。
 */
object MarkdownText {
    fun toPlainText(markdown: String): String {
        var text = markdown.replace("\r\n", "\n").replace('\r', '\n')

        // 去掉代码围栏，但保留代码内容。
        text = text.replace(Regex("(?s)```[^\\n]*\\n?(.*?)```"), "$1")
        text = text.replace(Regex("`([^`]*)`"), "$1")

        // 图片只保留 alt 文本。
        text = text.replace(Regex("!\\[([^]]*)]\\([^)]*\\)"), "$1")
        // 链接保留文字和地址，去掉 Markdown 括号语法。
        text = text.replace(Regex("\\[([^]]+)]\\(([^)]+)\\)"), "$1 ($2)")

        // 标题、引用、分隔线、任务列表。
        text = text.replace(Regex("(?m)^[ \\t]{0,3}#{1,6}[ \\t]*"), "")
        text = text.replace(Regex("(?m)^\\s{0,3}>\\s?"), "")
        text = text.replace(Regex("(?m)^\\s*([-*_]\\s*){3,}$"), "")
        text = text.replace(Regex("(?m)^\\s*[-*+]\\s+\\[[ xX]]\\s+"), "")

        // 粗体、斜体、删除线。
        text = text.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        text = text.replace(Regex("__([^_]+)__"), "$1")
        text = text.replace(Regex("~~([^~]+)~~"), "$1")
        text = text.replace(Regex("(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)"), "$1")
        text = text.replace(Regex("(?<!_)_([^_\\n]+)_(?!_)"), "$1")

        // 列表符号、HTML 标签、行尾空格。
        text = text.replace(Regex("(?m)^\\s*[-*+]\\s+"), "• ")
        text = text.replace(Regex("(?m)^\\s*\\d+[.)]\\s+"), "• ")
        text = text.replace(Regex("<[^>]+>"), "")
        text = text.replace(Regex("(?m)[ \\t]+$"), "")

        return text
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }
}
