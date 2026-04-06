package dev.misskey.mfm.internal

import dev.misskey.mfm.node.*

internal object Util {

    // -----------------------------------------------------------------------
    // mergeText — 隣接する MfmText ノードを結合
    // -----------------------------------------------------------------------

    fun mergeText(nodes: List<MfmNode>): List<MfmNode> {
        val result = mutableListOf<MfmNode>()
        for (node in nodes) {
            val last = result.lastOrNull()
            if (node is MfmText && last is MfmText) {
                result[result.size - 1] = MfmText(last.text + node.text)
            } else {
                result.add(node)
            }
        }
        return result
    }

    fun mergeTextInline(nodes: List<MfmInline>): List<MfmInline> {
        val result = mutableListOf<MfmInline>()
        for (node in nodes) {
            val last = result.lastOrNull()
            if (node is MfmText && last is MfmText) {
                result[result.size - 1] = MfmText(last.text + node.text)
            } else {
                result.add(node)
            }
        }
        return result
    }

    // -----------------------------------------------------------------------
    // stringify
    // -----------------------------------------------------------------------

    fun stringifyNode(node: MfmNode): String = when (node) {
        // Block
        is Quote -> node.children.joinToString("\n") { "> ${stringifyNode(it)}" }
        is Search -> "${node.query} [検索]"
        is CodeBlock -> "```${node.lang ?: ""}\n${node.code}\n```"
        is MathBlock -> "\\[${node.formula}\\]"
        is Center -> "<center>${node.children.joinToString("") { stringifyNode(it) }}</center>"
        // Inline
        is Bold -> "**${node.children.joinToString("") { stringifyNode(it) }}**"
        is Italic -> "*${node.children.joinToString("") { stringifyNode(it) }}*"
        is Strike -> "~~${node.children.joinToString("") { stringifyNode(it) }}~~"
        is Small -> "<small>${node.children.joinToString("") { stringifyNode(it) }}</small>"
        is Plain -> "<plain>${node.children.joinToString("") { it.text }}</plain>"
        is Fn -> {
            val argsStr = if (node.args.isEmpty()) "" else {
                "." + node.args.entries.joinToString(",") { (k, v) -> if (v != null) "$k=$v" else k }
            }
            "\$[${node.name}$argsStr ${node.children.joinToString("") { stringifyNode(it) }}]"
        }
        is Link -> "${if (node.silent) "?" else ""}[${node.children.joinToString("") { stringifyNode(it) }}](${node.url})"
        is Url -> if (node.brackets) "<${node.url}>" else node.url
        is Mention -> node.acct
        is Hashtag -> "#${node.hashtag}"
        is EmojiCode -> ":${node.name}:"
        is UnicodeEmoji -> node.emoji
        is InlineCode -> "`${node.code}`"
        is MathInline -> "\\(${node.formula}\\)"
        is MfmText -> node.text
        // Simple nodes
        is SimpleUnicodeEmoji -> node.emoji
        is SimpleEmojiCode -> ":${node.name}:"
        is SimpleText -> node.text
        is SimplePlain -> "<plain>${node.children.joinToString("") { it.text }}</plain>"
    }

    private enum class StringifyState { NONE, INLINE, BLOCK }

    fun stringifyTree(nodes: List<MfmNode>): String {
        val sb = StringBuilder()
        var state = StringifyState.NONE
        for (node in nodes) {
            val isBlock = node.isMfmBlock()
            if (isBlock && state == StringifyState.INLINE) sb.append('\n')
            else if (isBlock && state == StringifyState.BLOCK) sb.append('\n')
            sb.append(stringifyNode(node))
            state = if (isBlock) StringifyState.BLOCK else StringifyState.INLINE
        }
        return sb.toString()
    }

    // -----------------------------------------------------------------------
    // inspect / extract
    // -----------------------------------------------------------------------

    fun inspectOne(node: MfmNode, action: (MfmNode) -> Unit) {
        action(node)
        val children: List<MfmNode> = when (node) {
            is Quote -> node.children
            is Center -> node.children
            is Bold -> node.children
            is Italic -> node.children
            is Strike -> node.children
            is Small -> node.children
            is Plain -> node.children
            is Fn -> node.children
            is Link -> node.children
            else -> emptyList()
        }
        children.forEach { inspectOne(it, action) }
    }
}
