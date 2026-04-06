package dev.misskey.mfm.parser

import dev.misskey.mfm.internal.EmojiUtil
import dev.misskey.mfm.node.*
import dev.misskey.mfm.parser.core.*

internal object SimpleParser {

    fun parse(input: String): List<MfmSimpleNode> {
        val state = State()
        val nodes = mutableListOf<MfmSimpleNode>()
        var cur = 0
        while (cur < input.length) {
            // unicode emoji
            val emoji = EmojiUtil.matchAt(input, cur)
            if (emoji != null) {
                nodes.add(SimpleUnicodeEmoji(emoji))
                cur += emoji.length
                continue
            }
            // emoji code :name:
            val emojiCodeRegex = Regex("""^:([a-z0-9_+\-]+):""")
            val emojiMatch = emojiCodeRegex.find(input, cur)
            if (emojiMatch != null && emojiMatch.range.first == cur) {
                nodes.add(SimpleEmojiCode(emojiMatch.groupValues[1]))
                cur = emojiMatch.range.last + 1
                continue
            }
            // plain tag
            if (input.startsWith("<plain>", cur)) {
                val end = input.indexOf("</plain>", cur + 7)
                if (end >= 0) {
                    val text = input.substring(cur + 7, end)
                    nodes.add(SimplePlain(listOf(SimpleText(text))))
                    cur = end + 8
                    continue
                }
            }
            // single char as text
            nodes.add(SimpleText(input[cur].toString()))
            cur++
        }
        return mergeSimpleText(nodes)
    }

    private fun mergeSimpleText(nodes: List<MfmSimpleNode>): List<MfmSimpleNode> {
        val result = mutableListOf<MfmSimpleNode>()
        for (node in nodes) {
            val last = result.lastOrNull()
            if (node is SimpleText && last is SimpleText) {
                result[result.size - 1] = SimpleText(last.text + node.text)
            } else {
                result.add(node)
            }
        }
        return result
    }
}
