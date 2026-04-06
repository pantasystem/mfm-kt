package dev.misskey.mfm.parser

import dev.misskey.mfm.internal.EmojiUtil
import dev.misskey.mfm.internal.Util
import dev.misskey.mfm.node.*
import dev.misskey.mfm.parser.core.*

/**
 * MFM フルパーサー。
 *
 * 設計メモ:
 * - 正規表現は `^` アンカーを使わず、`find(input, index)` + `match.range.first == index` で
 *   "現在位置から始まるマッチ" を判定する。
 *   (`^` は MULTILINE なしだと常に文字列先頭にアンカーされるため index > 0 で使えない)
 */
internal object FullParser {

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------

    fun parse(input: String, nestLimit: Int = 20): List<MfmNode> {
        val state = State(nestLimit = nestLimit)
        val nodes = mutableListOf<MfmNode>()
        var cur = 0
        while (cur < input.length) {
            var matched = false
            for (p in blockParsers) {
                when (val r = p.parse(input, cur, state)) {
                    is Success -> { nodes.add(r.value); cur = r.index; matched = true; break }
                    is Failure -> continue
                }
            }
            if (matched) continue
            for (p in inlineParsers) {
                when (val r = p.parse(input, cur, state)) {
                    is Success -> { nodes.add(r.value); cur = r.index; matched = true; break }
                    is Failure -> continue
                }
            }
            if (!matched) { nodes.add(MfmText(input[cur].toString())); cur++ }
        }
        return Util.mergeText(nodes)
    }

    // -----------------------------------------------------------------------
    // Inline parser (reused for children)
    // -----------------------------------------------------------------------

    private fun parseInline(input: String, state: State): List<MfmInline> {
        val nodes = mutableListOf<MfmInline>()
        var cur = 0
        while (cur < input.length) {
            var matched = false
            for (p in inlineParsers) {
                when (val r = p.parse(input, cur, state)) {
                    is Success -> { nodes.add(r.value); cur = r.index; matched = true; break }
                    is Failure -> continue
                }
            }
            if (!matched) { nodes.add(MfmText(input[cur].toString())); cur++ }
        }
        return Util.mergeTextInline(nodes)
    }

    private fun nestInline(inner: String, state: State): List<MfmInline> =
        if (state.depth < state.nestLimit) {
            state.depth++
            val r = parseInline(inner, state)
            state.depth--
            r
        } else emptyList()

    // -----------------------------------------------------------------------
    // Block parsers
    // -----------------------------------------------------------------------

    /** `> line` の引用ブロック */
    private val quoteParser: Parser<Quote> = Parser { input, index, state ->
        if (index > 0 && input[index - 1] != '\n') return@Parser Failure
        if (index >= input.length || input[index] != '>') return@Parser Failure
        val lineRegex = Regex("> ?(.*)")
        val lines = mutableListOf<String>()
        var cur = index
        while (cur < input.length) {
            val match = lineRegex.find(input, cur)
            if (match == null || match.range.first != cur) break
            lines.add(match.groupValues[1])
            cur = match.range.last + 1
            // 改行を消費
            if (cur < input.length) {
                if (input[cur] == '\r' && cur + 1 < input.length && input[cur + 1] == '\n') cur += 2
                else if (input[cur] == '\r' || input[cur] == '\n') cur++
            }
            if (cur >= input.length || input[cur] != '>') break
        }
        if (lines.isEmpty()) return@Parser Failure
        val inner = lines.joinToString("\n")
        val children = if (state.depth < state.nestLimit) parse(inner, state.nestLimit - 1) else emptyList()
        Success(Quote(children), cur)
    }

    /** ` ```lang\ncode\n``` ` */
    private val codeBlockParser: Parser<CodeBlock> = Parser { input, index, _ ->
        if (index > 0 && input[index - 1] != '\n') return@Parser Failure
        val regex = Regex("```([^\\n]*)\\n([\\s\\S]+?)\\n```(?:\\n|\$)")
        val match = regex.find(input, index) ?: return@Parser Failure
        if (match.range.first != index) return@Parser Failure
        val lang = match.groupValues[1].trim().ifEmpty { null }
        Success(CodeBlock(match.groupValues[2], lang), match.range.last + 1)
    }

    /** `\[formula\]` */
    private val mathBlockParser: Parser<MathBlock> = Parser { input, index, _ ->
        if (index > 0 && input[index - 1] != '\n') return@Parser Failure
        val regex = Regex("\\\\\\[([\\s\\S]+?)\\\\\\](?:\\n|\$)")
        val match = regex.find(input, index) ?: return@Parser Failure
        if (match.range.first != index) return@Parser Failure
        Success(MathBlock(match.groupValues[1].trim()), match.range.last + 1)
    }

    /** `<center>...</center>` */
    private val centerParser: Parser<Center> = Parser { input, index, state ->
        if (index > 0 && input[index - 1] != '\n') return@Parser Failure
        val regex = Regex("<center>([\\s\\S]+?)</center>(?:\\n|\$)")
        val match = regex.find(input, index) ?: return@Parser Failure
        if (match.range.first != index) return@Parser Failure
        val children = nestInline(match.groupValues[1], state)
        Success(Center(children), match.range.last + 1)
    }

    /** `query [検索]` */
    private val searchParser: Parser<Search> = Parser { input, index, _ ->
        if (index > 0 && input[index - 1] != '\n') return@Parser Failure
        val regex = Regex("(.+)\\s\\[(検索|[Ss]earch)\\](?:\\n|\$)")
        val match = regex.find(input, index) ?: return@Parser Failure
        if (match.range.first != index) return@Parser Failure
        val query = match.groupValues[1]
        val content = match.value.trimEnd('\n', '\r')
        Success(Search(query, content), match.range.last + 1)
    }

    // -----------------------------------------------------------------------
    // Inline parsers
    // -----------------------------------------------------------------------

    private val unicodeEmojiParser: Parser<UnicodeEmoji> = Parser { input, index, _ ->
        val emoji = EmojiUtil.matchAt(input, index) ?: return@Parser Failure
        Success(UnicodeEmoji(emoji), index + emoji.length)
    }

    /** `:name:` */
    private val emojiCodeParser: Parser<EmojiCode> = Parser { input, index, _ ->
        if (index > 0 && input[index - 1].isLetterOrDigit()) return@Parser Failure
        val regex = Regex(":([a-z0-9_+\\-]+):")
        val match = regex.find(input, index) ?: return@Parser Failure
        if (match.range.first != index) return@Parser Failure
        val after = index + match.value.length
        if (after < input.length && input[after].isLetterOrDigit()) return@Parser Failure
        Success(EmojiCode(match.groupValues[1]), match.range.last + 1)
    }

    /** `**...**` */
    private val boldAstaParser: Parser<Bold> = Parser { input, index, state ->
        if (!input.startsWith("**", index)) return@Parser Failure
        val end = input.indexOf("**", index + 2)
        if (end < 0) return@Parser Failure
        val inner = input.substring(index + 2, end)
        if (inner.isEmpty()) return@Parser Failure
        Success(Bold(nestInline(inner, state)), end + 2)
    }

    /** `<b>...</b>` */
    private val boldTagParser: Parser<Bold> = Parser { input, index, state ->
        val regex = Regex("<b>([\\s\\S]+?)</b>")
        val match = regex.find(input, index) ?: return@Parser Failure
        if (match.range.first != index) return@Parser Failure
        Success(Bold(nestInline(match.groupValues[1], state)), match.range.last + 1)
    }

    /** `__text__` (英数字・空白のみ) */
    private val boldUnderParser: Parser<Bold> = Parser { input, index, _ ->
        val regex = Regex("__([a-zA-Z0-9\\u0020\\u3000\\t]+)__")
        val match = regex.find(input, index) ?: return@Parser Failure
        if (match.range.first != index) return@Parser Failure
        Success(Bold(listOf(MfmText(match.groupValues[1]))), match.range.last + 1)
    }

    /** `*text*` */
    private val italicAstaParser: Parser<Italic> = Parser { input, index, state ->
        if (index > 0 && input[index - 1].isLetterOrDigit()) return@Parser Failure
        val regex = Regex("\\*([^\\s*]+)\\*")
        val match = regex.find(input, index) ?: return@Parser Failure
        if (match.range.first != index) return@Parser Failure
        Success(Italic(nestInline(match.groupValues[1], state)), match.range.last + 1)
    }

    /** `_text_` */
    private val italicUnderParser: Parser<Italic> = Parser { input, index, _ ->
        if (index > 0 && input[index - 1].isLetterOrDigit()) return@Parser Failure
        val regex = Regex("_([^\\s_]+)_")
        val match = regex.find(input, index) ?: return@Parser Failure
        if (match.range.first != index) return@Parser Failure
        Success(Italic(listOf(MfmText(match.groupValues[1]))), match.range.last + 1)
    }

    /** `<i>...</i>` */
    private val italicTagParser: Parser<Italic> = Parser { input, index, state ->
        val regex = Regex("<i>([\\s\\S]+?)</i>")
        val match = regex.find(input, index) ?: return@Parser Failure
        if (match.range.first != index) return@Parser Failure
        Success(Italic(nestInline(match.groupValues[1], state)), match.range.last + 1)
    }

    /** `~~...~~` */
    private val strikeWaveParser: Parser<Strike> = Parser { input, index, state ->
        val regex = Regex("~~([\\s\\S]+?)~~")
        val match = regex.find(input, index) ?: return@Parser Failure
        if (match.range.first != index) return@Parser Failure
        Success(Strike(nestInline(match.groupValues[1], state)), match.range.last + 1)
    }

    /** `<s>...</s>` */
    private val strikeTagParser: Parser<Strike> = Parser { input, index, state ->
        val regex = Regex("<s>([\\s\\S]+?)</s>")
        val match = regex.find(input, index) ?: return@Parser Failure
        if (match.range.first != index) return@Parser Failure
        Success(Strike(nestInline(match.groupValues[1], state)), match.range.last + 1)
    }

    /** `<small>...</small>` */
    private val smallTagParser: Parser<Small> = Parser { input, index, state ->
        val regex = Regex("<small>([\\s\\S]+?)</small>")
        val match = regex.find(input, index) ?: return@Parser Failure
        if (match.range.first != index) return@Parser Failure
        Success(Small(nestInline(match.groupValues[1], state)), match.range.last + 1)
    }

    /** `<plain>...</plain>` */
    private val plainTagParser: Parser<Plain> = Parser { input, index, _ ->
        val regex = Regex("<plain>([\\s\\S]+?)</plain>")
        val match = regex.find(input, index) ?: return@Parser Failure
        if (match.range.first != index) return@Parser Failure
        Success(Plain(listOf(MfmText(match.groupValues[1]))), match.range.last + 1)
    }

    /** `` `code` `` */
    private val inlineCodeParser: Parser<InlineCode> = Parser { input, index, _ ->
        val regex = Regex("`([^`\u00b4\\n]+)`")
        val match = regex.find(input, index) ?: return@Parser Failure
        if (match.range.first != index) return@Parser Failure
        Success(InlineCode(match.groupValues[1]), match.range.last + 1)
    }

    /** `\(formula\)` */
    private val mathInlineParser: Parser<MathInline> = Parser { input, index, _ ->
        val regex = Regex("\\\\\\(([^\\)\\n]+)\\\\\\)")
        val match = regex.find(input, index) ?: return@Parser Failure
        if (match.range.first != index) return@Parser Failure
        Success(MathInline(match.groupValues[1]), match.range.last + 1)
    }

    /** `@user` or `@user@host` */
    private val mentionParser: Parser<Mention> = Parser { input, index, state ->
        if (state.linkLabel) return@Parser Failure
        if (index > 0 && input[index - 1].isLetterOrDigit()) return@Parser Failure
        if (index >= input.length || input[index] != '@') return@Parser Failure
        val regex = Regex("@([a-zA-Z0-9_.-]+)(?:@([a-zA-Z0-9_.\\-]+\\.[a-zA-Z0-9]+))?")
        val match = regex.find(input, index) ?: return@Parser Failure
        if (match.range.first != index) return@Parser Failure
        val username = match.groupValues[1].trimEnd('.', '-')
        if (username.isEmpty()) return@Parser Failure
        val host = match.groupValues[2].ifEmpty { null }?.trimEnd('.', '-')
        val acct = if (host != null) "@$username@$host" else "@$username"
        val endIndex = index + 1 + username.length + (if (host != null) 1 + host.length else 0)
        Success(Mention(username, host, acct), endIndex)
    }

    /** `#tag` */
    private val hashtagParser: Parser<Hashtag> = Parser { input, index, state ->
        if (state.linkLabel) return@Parser Failure
        if (index > 0) {
            val prev = input[index - 1]
            if (prev != ' ' && prev != '\u3000' && prev != '\t' && prev != '\n' && prev != '\r') return@Parser Failure
        }
        if (index >= input.length || input[index] != '#') return@Parser Failure
        val regex = Regex("#([^\\s\\u3000\\t.,!?'\"#:/\\[\\]【】()「」（）<>]+)")
        val match = regex.find(input, index) ?: return@Parser Failure
        if (match.range.first != index) return@Parser Failure
        val tag = match.groupValues[1]
        if (tag.isEmpty() || tag.all { it.isDigit() }) return@Parser Failure
        Success(Hashtag(tag), match.range.last + 1)
    }

    /** `https://...` or `<https://...>` */
    private val urlParser: Parser<Url> = Parser { input, index, state ->
        if (state.linkLabel) return@Parser Failure
        // angle-bracket URL
        if (index < input.length && input[index] == '<') {
            val altRegex = Regex("<(https?://[^>\\s]+)>")
            val altMatch = altRegex.find(input, index)
            if (altMatch != null && altMatch.range.first == index) {
                return@Parser Success(Url(altMatch.groupValues[1], brackets = true), altMatch.range.last + 1)
            }
        }
        // bare URL
        val regex = Regex("https?://[\\w/:%#@\$&?!()\\[\\]~.=+\\-]+")
        val match = regex.find(input, index) ?: return@Parser Failure
        if (match.range.first != index) return@Parser Failure
        val url = match.value.trimEnd('.', ',')
        Success(Url(url, brackets = false), index + url.length)
    }

    /** `[label](url)` or `?[label](url)` */
    private val linkParser: Parser<Link> = Parser { input, index, state ->
        val silent = input.startsWith("?[", index)
        val labelStart = when {
            silent -> index + 2
            index < input.length && input[index] == '[' -> index + 1
            else -> return@Parser Failure
        }
        val labelEnd = input.indexOf(']', labelStart)
        if (labelEnd < 0 || labelEnd + 1 >= input.length || input[labelEnd + 1] != '(') return@Parser Failure
        val urlStart = labelEnd + 2
        val urlEnd = input.indexOf(')', urlStart)
        if (urlEnd < 0) return@Parser Failure
        val url = input.substring(urlStart, urlEnd)
        if (!url.startsWith("http://") && !url.startsWith("https://")) return@Parser Failure
        val labelText = input.substring(labelStart, labelEnd)
        val prevLinkLabel = state.linkLabel
        state.linkLabel = true
        val children = nestInline(labelText, state)
        state.linkLabel = prevLinkLabel
        Success(Link(url, children, silent), urlEnd + 1)
    }

    /** `$[name.arg1,arg2=val content]` */
    private val fnParser: Parser<Fn> = Parser { input, index, state ->
        if (!input.startsWith("\$[", index)) return@Parser Failure
        // name
        val nameRegex = Regex("\\\$\\[([a-z0-9_]+)")
        val nameMatch = nameRegex.find(input, index) ?: return@Parser Failure
        if (nameMatch.range.first != index) return@Parser Failure
        val name = nameMatch.groupValues[1]
        var cur = nameMatch.range.last + 1

        // args: .arg1,arg2=val,...
        val args = mutableMapOf<String, String?>()
        if (cur < input.length && input[cur] == '.') {
            cur++
            val argRegex = Regex("([a-z0-9_]+)(?:=([a-z0-9_.]+))?")
            while (cur < input.length) {
                val argMatch = argRegex.find(input, cur) ?: break
                if (argMatch.range.first != cur) break
                args[argMatch.groupValues[1]] = argMatch.groupValues[2].ifEmpty { null }
                cur = argMatch.range.last + 1
                if (cur < input.length && input[cur] == ',') cur++ else break
            }
        }

        // space before content
        if (cur >= input.length || input[cur] != ' ') return@Parser Failure
        cur++

        // content up to matching ]
        val contentStart = cur
        var depth = 1
        while (cur < input.length && depth > 0) {
            when {
                input.startsWith("\$[", cur) -> { depth++; cur += 2 }
                input[cur] == ']' -> { depth--; if (depth > 0) cur++ }
                else -> cur++
            }
        }
        if (depth != 0) return@Parser Failure
        val content = input.substring(contentStart, cur)
        Success(Fn(name, args, nestInline(content, state)), cur + 1)
    }

    // -----------------------------------------------------------------------
    // Parser lists (順序重要: より具体的なものを先に)
    // -----------------------------------------------------------------------

    private val blockParsers: List<Parser<out MfmBlock>> = listOf(
        quoteParser,
        codeBlockParser,
        mathBlockParser,
        centerParser,
        searchParser,
    )

    private val inlineParsers: List<Parser<out MfmInline>> = listOf(
        unicodeEmojiParser,
        emojiCodeParser,
        fnParser,
        plainTagParser,
        boldAstaParser,
        boldTagParser,
        boldUnderParser,
        italicAstaParser,
        italicUnderParser,
        italicTagParser,
        strikeWaveParser,
        strikeTagParser,
        smallTagParser,
        inlineCodeParser,
        mathInlineParser,
        mentionParser,
        hashtagParser,
        linkParser,
        urlParser,
    )
}
