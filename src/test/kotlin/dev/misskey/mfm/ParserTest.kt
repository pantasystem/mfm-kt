package dev.misskey.mfm

import dev.misskey.mfm.node.*
import kotlin.test.*

class ParserTest {

    // -----------------------------------------------------------------------
    // Block nodes
    // -----------------------------------------------------------------------

    @Test fun `codeBlock basic`() {
        val nodes = Mfm.parse("```\nabc\n```\n")
        assertEquals(listOf(CodeBlock("abc", null)), nodes)
    }

    @Test fun `codeBlock with lang`() {
        val nodes = Mfm.parse("```kotlin\nval x = 1\n```\n")
        assertEquals(listOf(CodeBlock("val x = 1", "kotlin")), nodes)
    }

    @Test fun `mathBlock`() {
        val nodes = Mfm.parse("\\[E=mc^2\\]\n")
        assertEquals(listOf(MathBlock("E=mc^2")), nodes)
    }

    @Test fun `center`() {
        val nodes = Mfm.parse("<center>abc</center>\n")
        assertEquals(listOf(Center(listOf(MfmText("abc")))), nodes)
    }

    @Test fun `search`() {
        val nodes = Mfm.parse("Misskey [検索]\n")
        val node = nodes.filterIsInstance<Search>().firstOrNull()
        assertNotNull(node)
        assertEquals("Misskey", node.query)
    }

    // -----------------------------------------------------------------------
    // Inline nodes
    // -----------------------------------------------------------------------

    @Test fun `bold asta`() {
        val nodes = Mfm.parse("**Hello**")
        assertEquals(listOf(Bold(listOf(MfmText("Hello")))), nodes)
    }

    @Test fun `bold tag`() {
        val nodes = Mfm.parse("<b>Hello</b>")
        assertEquals(listOf(Bold(listOf(MfmText("Hello")))), nodes)
    }

    @Test fun `bold under`() {
        val nodes = Mfm.parse("__Hello__")
        assertEquals(listOf(Bold(listOf(MfmText("Hello")))), nodes)
    }

    @Test fun `italic asta`() {
        val nodes = Mfm.parse("*Hello*")
        val italic = nodes.filterIsInstance<Italic>().firstOrNull()
        assertNotNull(italic)
    }

    @Test fun `italic tag`() {
        val nodes = Mfm.parse("<i>Hello</i>")
        val italic = nodes.filterIsInstance<Italic>().firstOrNull()
        assertNotNull(italic)
    }

    @Test fun `strike wave`() {
        val nodes = Mfm.parse("~~Hello~~")
        val strike = nodes.filterIsInstance<Strike>().firstOrNull()
        assertNotNull(strike)
        assertEquals(listOf(MfmText("Hello")), strike.children)
    }

    @Test fun `inline code`() {
        val nodes = Mfm.parse("`code`")
        assertEquals(listOf(InlineCode("code")), nodes)
    }

    @Test fun `math inline`() {
        val nodes = Mfm.parse("\\(E=mc^2\\)")
        assertEquals(listOf(MathInline("E=mc^2")), nodes)
    }

    @Test fun `mention local`() {
        val nodes = Mfm.parse("@alice ")
        val mention = nodes.filterIsInstance<Mention>().firstOrNull()
        assertNotNull(mention)
        assertEquals("alice", mention.username)
        assertNull(mention.host)
    }

    @Test fun `mention remote`() {
        val nodes = Mfm.parse("@alice@example.com")
        val mention = nodes.filterIsInstance<Mention>().firstOrNull()
        assertNotNull(mention)
        assertEquals("alice", mention.username)
        assertEquals("example.com", mention.host)
    }

    @Test fun `emoji code`() {
        val nodes = Mfm.parse(":foo:")
        assertEquals(listOf(EmojiCode("foo")), nodes)
    }

    // ---- カスタム絵文字の境界条件 (mfm.js仕様準拠) ----

    @Test fun `emoji code in Japanese sentence`() {
        // 日本語に挟まれた絵文字は認識される
        val nodes = Mfm.parse(":kawaii:に:nuigurumi:を合わせて:yosiyosi:する")
        val emojis = nodes.filterIsInstance<EmojiCode>()
        assertEquals(3, emojis.size)
        assertEquals("kawaii",    emojis[0].name)
        assertEquals("nuigurumi", emojis[1].name)
        assertEquals("yosiyosi",  emojis[2].name)
    }

    @Test fun `emoji code text nodes preserved in Japanese sentence`() {
        val nodes = Mfm.parse(":kawaii:に:nuigurumi:を合わせて:yosiyosi:する")
        val texts = nodes.filterIsInstance<MfmText>().map { it.text }
        assertTrue("に" in texts)
        assertTrue("を合わせて" in texts)
        assertTrue("する" in texts)
    }

    @Test fun `emoji code parsed even when preceded by ascii alphanumeric`() {
        // 直前の文字は無関係。abc:kawaii: → TEXT("abc") + EmojiCode("kawaii")
        val nodes = Mfm.parse("abc:kawaii:")
        val emojis = nodes.filterIsInstance<EmojiCode>()
        assertEquals(1, emojis.size)
        assertEquals("kawaii", emojis[0].name)
    }

    @Test fun `emoji code not parsed when followed by ascii alphanumeric`() {
        // 直後が ASCII 英数字の場合は絵文字にならない (mfm.js: foo:bar:baz → TEXT)
        val nodes = Mfm.parse("foo:bar:baz")
        assertEquals(0, nodes.filterIsInstance<EmojiCode>().size)
    }

    @Test fun `emoji code not parsed inside digits`() {
        // 数字に挟まれている場合もテキスト (mfm.js: 12:34:56 → TEXT)
        val nodes = Mfm.parse("12:34:56")
        assertEquals(0, nodes.filterIsInstance<EmojiCode>().size)
    }

    @Test fun `emoji code with hyphen and plus`() {
        val nodes = Mfm.parse(":flag-jp: :blobcat+1:")
        val emojis = nodes.filterIsInstance<EmojiCode>()
        assertEquals(2, emojis.size)
        assertEquals("flag-jp",   emojis[0].name)
        assertEquals("blobcat+1", emojis[1].name)
    }

    @Test fun `emoji code consecutive`() {
        val nodes = Mfm.parse(":foo::bar:")
        val emojis = nodes.filterIsInstance<EmojiCode>()
        assertEquals(2, emojis.size)
        assertEquals("foo", emojis[0].name)
        assertEquals("bar", emojis[1].name)
    }

    @Test fun `url bare`() {
        val nodes = Mfm.parse("https://example.com")
        val url = nodes.filterIsInstance<Url>().firstOrNull()
        assertNotNull(url)
        assertEquals("https://example.com", url.url)
        assertFalse(url.brackets)
    }

    @Test fun `url brackets`() {
        val nodes = Mfm.parse("<https://example.com>")
        val url = nodes.filterIsInstance<Url>().firstOrNull()
        assertNotNull(url)
        assertTrue(url.brackets)
    }

    @Test fun `link normal`() {
        val nodes = Mfm.parse("[MFM](https://example.com)")
        val link = nodes.filterIsInstance<Link>().firstOrNull()
        assertNotNull(link)
        assertEquals("https://example.com", link.url)
        assertFalse(link.silent)
    }

    @Test fun `link silent`() {
        val nodes = Mfm.parse("?[MFM](https://example.com)")
        val link = nodes.filterIsInstance<Link>().firstOrNull()
        assertNotNull(link)
        assertTrue(link.silent)
    }

    @Test fun `fn basic`() {
        val nodes = Mfm.parse("\$[tada Hello]")
        val fn = nodes.filterIsInstance<Fn>().firstOrNull()
        assertNotNull(fn)
        assertEquals("tada", fn.name)
    }

    @Test fun `fn with args`() {
        val nodes = Mfm.parse("\$[spin.speed=2s Hello]")
        val fn = nodes.filterIsInstance<Fn>().firstOrNull()
        assertNotNull(fn)
        assertEquals("spin", fn.name)
        assertEquals("2s", fn.args["speed"])
    }

    @Test fun `plain tag`() {
        val nodes = Mfm.parse("<plain>**not bold**</plain>")
        val plain = nodes.filterIsInstance<Plain>().firstOrNull()
        assertNotNull(plain)
        assertEquals("**not bold**", plain.children.firstOrNull()?.text)
    }

    // -----------------------------------------------------------------------
    // toString roundtrip
    // -----------------------------------------------------------------------

    @Test fun `toString bold`() {
        val original = "**Hello**"
        val nodes = Mfm.parse(original)
        assertEquals(original, Mfm.toString(nodes))
    }

    @Test fun `toString emoji`() {
        val original = ":foo:"
        val nodes = Mfm.parse(original)
        assertEquals(original, Mfm.toString(nodes))
    }

    // -----------------------------------------------------------------------
    // extract
    // -----------------------------------------------------------------------

    @Test fun `extract mentions`() {
        val nodes = Mfm.parse("Hello @alice and @bob@example.com")
        val mentions = Mfm.extract(nodes) { it as? Mention }
        assertEquals(2, mentions.size)
        assertEquals("alice", mentions[0].username)
        assertEquals("bob", mentions[1].username)
    }

    // -----------------------------------------------------------------------
    // SimpleParser
    // -----------------------------------------------------------------------

    @Test fun `parseSimple emoji code`() {
        val nodes = Mfm.parseSimple(":foo:")
        assertEquals(listOf(SimpleEmojiCode("foo")), nodes)
    }

    @Test fun `parseSimple text`() {
        val nodes = Mfm.parseSimple("hello")
        assertEquals(listOf(SimpleText("hello")), nodes)
    }

    @Test fun `parseSimple plain tag`() {
        val nodes = Mfm.parseSimple("<plain>abc</plain>")
        val plain = nodes.filterIsInstance<SimplePlain>().firstOrNull()
        assertNotNull(plain)
        assertEquals("abc", plain.children.firstOrNull()?.text)
    }

    @Test fun `parseSimple hashtag treated as text`() {
        val nodes = Mfm.parseSimple("#tag")
        // シンプルパーサーはハッシュタグを解析しない
        assertTrue(nodes.all { it is SimpleText || it is SimpleEmojiCode || it is SimpleUnicodeEmoji })
    }
}
