package dev.misskey.mfm

import dev.misskey.mfm.node.*
import kotlin.test.*

/**
 * misskey-hub MFM ドキュメントおよび mfm.js 仕様に基づくテストケース
 * https://misskey-hub.net/ja/docs/for-users/features/mfm/
 * https://github.com/misskey-dev/mfm.js
 */
class MfmDocsTest {

    // -----------------------------------------------------------------------
    // メンション
    // -----------------------------------------------------------------------

    @Test fun `mention - local`() {
        val nodes = Mfm.parse("@ai")
        val m = nodes.filterIsInstance<Mention>().firstOrNull()
        assertNotNull(m)
        assertEquals("ai", m.username)
        assertNull(m.host)
        assertEquals("@ai", m.acct)
    }

    @Test fun `mention - remote`() {
        val nodes = Mfm.parse("@ai@misskey.io")
        val m = nodes.filterIsInstance<Mention>().firstOrNull()
        assertNotNull(m)
        assertEquals("ai", m.username)
        assertEquals("misskey.io", m.host)
        assertEquals("@ai@misskey.io", m.acct)
    }

    @Test fun `mention - mail address is not a mention`() {
        // abc@example.com は Mention にならない（直前が英数字）
        val nodes = Mfm.parse("abc@example.com")
        assertEquals(0, nodes.filterIsInstance<Mention>().size)
    }

    // -----------------------------------------------------------------------
    // ハッシュタグ
    // -----------------------------------------------------------------------

    @Test fun `hashtag - basic`() {
        val nodes = Mfm.parse(" #misskey")
        val h = nodes.filterIsInstance<Hashtag>().firstOrNull()
        assertNotNull(h)
        assertEquals("misskey", h.hashtag)
    }

    @Test fun `hashtag - number only is not hashtag`() {
        val nodes = Mfm.parse(" #123")
        assertEquals(0, nodes.filterIsInstance<Hashtag>().size)
    }

    @Test fun `hashtag - not parsed inside link label`() {
        val nodes = Mfm.parse("[#tag](https://example.com)")
        assertEquals(0, nodes.filterIsInstance<Hashtag>().size)
    }

    // -----------------------------------------------------------------------
    // URL
    // -----------------------------------------------------------------------

    @Test fun `url - https`() {
        val nodes = Mfm.parse("https://example.com")
        val u = nodes.filterIsInstance<Url>().firstOrNull()
        assertNotNull(u)
        assertEquals("https://example.com", u.url)
        assertFalse(u.brackets)
    }

    @Test fun `url - trailing punctuation stripped`() {
        val nodes = Mfm.parse("https://example.com.")
        val u = nodes.filterIsInstance<Url>().firstOrNull()
        assertNotNull(u)
        assertEquals("https://example.com", u.url)
    }

    @Test fun `url - angle bracket form`() {
        val nodes = Mfm.parse("<https://example.com>")
        val u = nodes.filterIsInstance<Url>().firstOrNull()
        assertNotNull(u)
        assertTrue(u.brackets)
        assertEquals("https://example.com", u.url)
    }

    // -----------------------------------------------------------------------
    // リンク
    // -----------------------------------------------------------------------

    @Test fun `link - basic`() {
        val nodes = Mfm.parse("[example link](https://example.com)")
        val l = nodes.filterIsInstance<Link>().firstOrNull()
        assertNotNull(l)
        assertEquals("https://example.com", l.url)
        assertFalse(l.silent)
        assertEquals("example link", (l.children.firstOrNull() as? MfmText)?.text)
    }

    @Test fun `link - silent`() {
        val nodes = Mfm.parse("?[example link](https://example.com)")
        val l = nodes.filterIsInstance<Link>().firstOrNull()
        assertNotNull(l)
        assertTrue(l.silent)
    }

    // -----------------------------------------------------------------------
    // テキスト装飾
    // -----------------------------------------------------------------------

    @Test fun `bold - double asterisk`() {
        val nodes = Mfm.parse("**太字**")
        val b = nodes.filterIsInstance<Bold>().firstOrNull()
        assertNotNull(b)
        assertEquals("太字", (b.children.firstOrNull() as? MfmText)?.text)
    }

    @Test fun `italic - single asterisk`() {
        val nodes = Mfm.parse("*斜体*")
        assertNotNull(nodes.filterIsInstance<Italic>().firstOrNull())
    }

    @Test fun `strike - wave`() {
        val nodes = Mfm.parse("~~打ち消し~~")
        val s = nodes.filterIsInstance<Strike>().firstOrNull()
        assertNotNull(s)
        assertEquals("打ち消し", (s.children.firstOrNull() as? MfmText)?.text)
    }

    @Test fun `small - tag`() {
        val nodes = Mfm.parse("<small>MisskeyでFediverseの世界が広がります</small>")
        val s = nodes.filterIsInstance<Small>().firstOrNull()
        assertNotNull(s)
        assertEquals("MisskeyでFediverseの世界が広がります", (s.children.firstOrNull() as? MfmText)?.text)
    }

    // -----------------------------------------------------------------------
    // 引用
    // -----------------------------------------------------------------------

    @Test fun `quote - single line`() {
        val nodes = Mfm.parse("> MisskeyでFediverseの世界が広がります\n")
        val q = nodes.filterIsInstance<Quote>().firstOrNull()
        assertNotNull(q)
        val text = (q.children.firstOrNull() as? MfmText)?.text
        assertEquals("MisskeyでFediverseの世界が広がります", text)
    }

    @Test fun `quote - nested`() {
        val nodes = Mfm.parse(">> ネストされた引用\n")
        val outer = nodes.filterIsInstance<Quote>().firstOrNull()
        assertNotNull(outer)
        val inner = outer.children.filterIsInstance<Quote>().firstOrNull()
        assertNotNull(inner)
    }

    @Test fun `quote - multiline`() {
        val nodes = Mfm.parse("> line1\n> line2\n")
        val q = nodes.filterIsInstance<Quote>().firstOrNull()
        assertNotNull(q)
    }

    // -----------------------------------------------------------------------
    // コードブロック
    // -----------------------------------------------------------------------

    @Test fun `codeBlock - no lang`() {
        val nodes = Mfm.parse("```\nconsole.log(\"hello\")\n```\n")
        val c = nodes.filterIsInstance<CodeBlock>().firstOrNull()
        assertNotNull(c)
        assertNull(c.lang)
        assertEquals("console.log(\"hello\")", c.code)
    }

    @Test fun `codeBlock - with lang`() {
        val nodes = Mfm.parse("```ais\nfor (let i, 100) { }\n```\n")
        val c = nodes.filterIsInstance<CodeBlock>().firstOrNull()
        assertNotNull(c)
        assertEquals("ais", c.lang)
    }

    // -----------------------------------------------------------------------
    // 中央揃え
    // -----------------------------------------------------------------------

    @Test fun `center - japanese text`() {
        val nodes = Mfm.parse("<center>MisskeyでFediverseの世界が広がります</center>\n")
        val c = nodes.filterIsInstance<Center>().firstOrNull()
        assertNotNull(c)
        assertEquals("MisskeyでFediverseの世界が広がります", (c.children.firstOrNull() as? MfmText)?.text)
    }

    // -----------------------------------------------------------------------
    // 検索
    // -----------------------------------------------------------------------

    @Test fun `search - bracketed Japanese`() {
        val nodes = Mfm.parse("Misskey [検索]\n")
        val s = nodes.filterIsInstance<Search>().firstOrNull()
        assertNotNull(s)
        assertEquals("Misskey", s.query)
    }

    @Test fun `search - bracketed English`() {
        val nodes = Mfm.parse("Misskey [Search]\n")
        val s = nodes.filterIsInstance<Search>().firstOrNull()
        assertNotNull(s)
        assertEquals("Misskey", s.query)
    }

    @Test fun `search - bracketed lowercase`() {
        val nodes = Mfm.parse("Misskey [search]\n")
        val s = nodes.filterIsInstance<Search>().firstOrNull()
        assertNotNull(s)
        assertEquals("Misskey", s.query)
    }

    @Test fun `search - without brackets Japanese`() {
        // mfm.js では括弧なし形式も有効
        val nodes = Mfm.parse("Misskey 検索\n")
        val s = nodes.filterIsInstance<Search>().firstOrNull()
        assertNotNull(s)
        assertEquals("Misskey", s.query)
    }

    @Test fun `search - without brackets English`() {
        val nodes = Mfm.parse("Misskey Search\n")
        val s = nodes.filterIsInstance<Search>().firstOrNull()
        assertNotNull(s)
        assertEquals("Misskey", s.query)
    }

    // -----------------------------------------------------------------------
    // インラインコード / 数式
    // -----------------------------------------------------------------------

    @Test fun `inline code - AiScript`() {
        val nodes = Mfm.parse("`<: \"Hello, world!\"`")
        val c = nodes.filterIsInstance<InlineCode>().firstOrNull()
        assertNotNull(c)
        assertEquals("<: \"Hello, world!\"", c.code)
    }

    @Test fun `math inline`() {
        val nodes = Mfm.parse("\\(y = 2x\\)")
        val m = nodes.filterIsInstance<MathInline>().firstOrNull()
        assertNotNull(m)
        assertEquals("y = 2x", m.formula)
    }

    @Test fun `math block`() {
        val nodes = Mfm.parse("\\[a = 1\\]\n")
        val m = nodes.filterIsInstance<MathBlock>().firstOrNull()
        assertNotNull(m)
        assertEquals("a = 1", m.formula)
    }

    // -----------------------------------------------------------------------
    // plain
    // -----------------------------------------------------------------------

    @Test fun `plain - disables inner syntax`() {
        val nodes = Mfm.parse("<plain>**bold** @mention #hashtag `code` \$[x2 🍮]</plain>")
        val p = nodes.filterIsInstance<Plain>().firstOrNull()
        assertNotNull(p)
        // 内側はパースされずそのままテキスト
        val raw = p.children.firstOrNull()?.text ?: ""
        assertTrue(raw.contains("**bold**"))
        assertTrue(raw.contains("@mention"))
        assertTrue(raw.contains("#hashtag"))
    }

    // -----------------------------------------------------------------------
    // $[fn] — 文字装飾
    // -----------------------------------------------------------------------

    @Test fun `fn - ruby`() {
        val nodes = Mfm.parse("\$[ruby Misskey ミスキー]")
        val fn = nodes.filterIsInstance<Fn>().firstOrNull()
        assertNotNull(fn)
        assertEquals("ruby", fn.name)
        assertTrue(fn.args.isEmpty())
        assertEquals("Misskey ミスキー", (fn.children.firstOrNull() as? MfmText)?.text)
    }

    @Test fun `fn - unixtime`() {
        val nodes = Mfm.parse("\$[unixtime 1701356400]")
        val fn = nodes.filterIsInstance<Fn>().firstOrNull()
        assertNotNull(fn)
        assertEquals("unixtime", fn.name)
        assertEquals("1701356400", (fn.children.firstOrNull() as? MfmText)?.text)
    }

    @Test fun `fn - flip default`() {
        val nodes = Mfm.parse("\$[flip MisskeyでFediverseの世界が広がります]")
        val fn = nodes.filterIsInstance<Fn>().firstOrNull()
        assertNotNull(fn)
        assertEquals("flip", fn.name)
    }

    @Test fun `fn - flip vertical`() {
        val nodes = Mfm.parse("\$[flip.v text]")
        val fn = nodes.filterIsInstance<Fn>().firstOrNull()
        assertNotNull(fn)
        assertTrue(fn.args.containsKey("v"))
    }

    @Test fun `fn - flip both`() {
        val nodes = Mfm.parse("\$[flip.h,v text]")
        val fn = nodes.filterIsInstance<Fn>().firstOrNull()
        assertNotNull(fn)
        assertTrue(fn.args.containsKey("h"))
        assertTrue(fn.args.containsKey("v"))
    }

    @Test fun `fn - font serif`() {
        val nodes = Mfm.parse("\$[font.serif MisskeyでFediverseの世界が広がります]")
        val fn = nodes.filterIsInstance<Fn>().firstOrNull()
        assertNotNull(fn)
        assertEquals("font", fn.name)
        assertTrue(fn.args.containsKey("serif"))
    }

    @Test fun `fn - font monospace`() {
        val nodes = Mfm.parse("\$[font.monospace text]")
        val fn = nodes.filterIsInstance<Fn>().firstOrNull()
        assertNotNull(fn); assertEquals("font", fn.name)
        assertTrue(fn.args.containsKey("monospace"))
    }

    @Test fun `fn - blur`() {
        val nodes = Mfm.parse("\$[blur もりもりあんこ]")
        val fn = nodes.filterIsInstance<Fn>().firstOrNull()
        assertNotNull(fn)
        assertEquals("blur", fn.name)
    }

    @Test fun `fn - fg color uppercase hex`() {
        val nodes = Mfm.parse("\$[fg.color=3CB371 【お知らせ】]")
        val fn = nodes.filterIsInstance<Fn>().firstOrNull()
        assertNotNull(fn)
        assertEquals("fg", fn.name)
        assertEquals("3CB371", fn.args["color"])
    }

    @Test fun `fn - bg color`() {
        val nodes = Mfm.parse("\$[bg.color=ff0 黄背景]")
        val fn = nodes.filterIsInstance<Fn>().firstOrNull()
        assertNotNull(fn)
        assertEquals("bg", fn.name)
        assertEquals("ff0", fn.args["color"])
    }

    @Test fun `fn - border simple`() {
        val nodes = Mfm.parse("\$[border シンプルな枠線]")
        val fn = nodes.filterIsInstance<Fn>().firstOrNull()
        assertNotNull(fn); assertEquals("border", fn.name)
    }

    @Test fun `fn - border with multiple args`() {
        val nodes = Mfm.parse("\$[border.style=dotted,width=2 ドット枠線]")
        val fn = nodes.filterIsInstance<Fn>().firstOrNull()
        assertNotNull(fn)
        assertEquals("dotted", fn.args["style"])
        assertEquals("2", fn.args["width"])
    }

    @Test fun `fn - rotate`() {
        val nodes = Mfm.parse("\$[rotate.deg=30 misskey]")
        val fn = nodes.filterIsInstance<Fn>().firstOrNull()
        assertNotNull(fn)
        assertEquals("rotate", fn.name)
        assertEquals("30", fn.args["deg"])
    }

    @Test fun `fn - position`() {
        val nodes = Mfm.parse("\$[position.x=0.8,y=0.5 🍮]")
        val fn = nodes.filterIsInstance<Fn>().firstOrNull()
        assertNotNull(fn)
        assertEquals("0.8", fn.args["x"])
        assertEquals("0.5", fn.args["y"])
    }

    @Test fun `fn - scale`() {
        val nodes = Mfm.parse("\$[scale.x=4,y=2 🍮]")
        val fn = nodes.filterIsInstance<Fn>().firstOrNull()
        assertNotNull(fn)
        assertEquals("4", fn.args["x"])
        assertEquals("2", fn.args["y"])
    }

    @Test fun `fn - x2`() {
        val nodes = Mfm.parse("\$[x2 x2]")
        val fn = nodes.filterIsInstance<Fn>().firstOrNull()
        assertNotNull(fn); assertEquals("x2", fn.name)
    }

    @Test fun `fn - x3`() {
        val nodes = Mfm.parse("\$[x3 x3]")
        assertEquals("x3", nodes.filterIsInstance<Fn>().firstOrNull()?.name)
    }

    @Test fun `fn - x4`() {
        val nodes = Mfm.parse("\$[x4 x4]")
        assertEquals("x4", nodes.filterIsInstance<Fn>().firstOrNull()?.name)
    }

    // -----------------------------------------------------------------------
    // $[fn] — アニメーション
    // -----------------------------------------------------------------------

    @Test fun `fn - jelly`() {
        val nodes = Mfm.parse("\$[jelly 🍮]")
        assertEquals("jelly", nodes.filterIsInstance<Fn>().firstOrNull()?.name)
    }

    @Test fun `fn - jelly with speed`() {
        val nodes = Mfm.parse("\$[jelly.speed=5s 🍮]")
        val fn = nodes.filterIsInstance<Fn>().firstOrNull()
        assertNotNull(fn)
        assertEquals("5s", fn.args["speed"])
    }

    @Test fun `fn - tada`() {
        assertEquals("tada", Mfm.parse("\$[tada 🍮]").filterIsInstance<Fn>().firstOrNull()?.name)
    }

    @Test fun `fn - jump`() {
        assertEquals("jump", Mfm.parse("\$[jump 🍮]").filterIsInstance<Fn>().firstOrNull()?.name)
    }

    @Test fun `fn - bounce`() {
        assertEquals("bounce", Mfm.parse("\$[bounce 🍮]").filterIsInstance<Fn>().firstOrNull()?.name)
    }

    @Test fun `fn - spin`() {
        assertEquals("spin", Mfm.parse("\$[spin 🍮]").filterIsInstance<Fn>().firstOrNull()?.name)
    }

    @Test fun `fn - spin left`() {
        val fn = Mfm.parse("\$[spin.left 🍮]").filterIsInstance<Fn>().firstOrNull()
        assertNotNull(fn); assertTrue(fn.args.containsKey("left"))
    }

    @Test fun `fn - spin alternate`() {
        val fn = Mfm.parse("\$[spin.alternate 🍮]").filterIsInstance<Fn>().firstOrNull()
        assertNotNull(fn); assertTrue(fn.args.containsKey("alternate"))
    }

    @Test fun `fn - spin x axis`() {
        val fn = Mfm.parse("\$[spin.x 🍮]").filterIsInstance<Fn>().firstOrNull()
        assertNotNull(fn); assertTrue(fn.args.containsKey("x"))
    }

    @Test fun `fn - spin y axis`() {
        val fn = Mfm.parse("\$[spin.y 🍮]").filterIsInstance<Fn>().firstOrNull()
        assertNotNull(fn); assertTrue(fn.args.containsKey("y"))
    }

    @Test fun `fn - spin with speed`() {
        val fn = Mfm.parse("\$[spin.speed=5s 🍮]").filterIsInstance<Fn>().firstOrNull()
        assertNotNull(fn); assertEquals("5s", fn.args["speed"])
    }

    @Test fun `fn - shake`() {
        assertEquals("shake", Mfm.parse("\$[shake 🍮]").filterIsInstance<Fn>().firstOrNull()?.name)
    }

    @Test fun `fn - twitch`() {
        assertEquals("twitch", Mfm.parse("\$[twitch 🍮]").filterIsInstance<Fn>().firstOrNull()?.name)
    }

    @Test fun `fn - rainbow`() {
        assertEquals("rainbow", Mfm.parse("\$[rainbow 🍮]").filterIsInstance<Fn>().firstOrNull()?.name)
    }

    @Test fun `fn - rainbow with speed`() {
        val fn = Mfm.parse("\$[rainbow.speed=5s 🍮]").filterIsInstance<Fn>().firstOrNull()
        assertNotNull(fn); assertEquals("5s", fn.args["speed"])
    }

    @Test fun `fn - sparkle`() {
        assertEquals("sparkle", Mfm.parse("\$[sparkle 🍮]").filterIsInstance<Fn>().firstOrNull()?.name)
    }

    // -----------------------------------------------------------------------
    // ネスト
    // -----------------------------------------------------------------------

    @Test fun `fn - nested`() {
        val nodes = Mfm.parse("\$[rainbow \$[fg.color=f0f 色付き文字]]")
        val outer = nodes.filterIsInstance<Fn>().firstOrNull()
        assertNotNull(outer)
        assertEquals("rainbow", outer.name)
        val inner = outer.children.filterIsInstance<Fn>().firstOrNull()
        assertNotNull(inner)
        assertEquals("fg", inner.name)
        assertEquals("f0f", inner.args["color"])
    }

    @Test fun `bold containing italic`() {
        val nodes = Mfm.parse("**太字と*斜体*の組み合わせ**")
        val bold = nodes.filterIsInstance<Bold>().firstOrNull()
        assertNotNull(bold)
        val italic = bold.children.filterIsInstance<Italic>().firstOrNull()
        assertNotNull(italic)
    }

    // -----------------------------------------------------------------------
    // 複合文
    // -----------------------------------------------------------------------

    @Test fun `mixed - mention hashtag emoji in one line`() {
        val nodes = Mfm.parse("@ai #misskey :misskey:")
        assertTrue(nodes.filterIsInstance<Mention>().isNotEmpty())
        assertTrue(nodes.filterIsInstance<Hashtag>().isNotEmpty())
        assertTrue(nodes.filterIsInstance<EmojiCode>().isNotEmpty())
    }
}
