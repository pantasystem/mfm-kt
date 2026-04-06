package dev.misskey.mfm.node

// ---------------------------------------------------------------------------
// Root
// ---------------------------------------------------------------------------

sealed class MfmNode

// ---------------------------------------------------------------------------
// Block nodes
// ---------------------------------------------------------------------------

sealed class MfmBlock : MfmNode()

data class Quote(val children: List<MfmNode>) : MfmBlock()

data class Search(val query: String, val content: String) : MfmBlock()

data class CodeBlock(val code: String, val lang: String?) : MfmBlock()

data class MathBlock(val formula: String) : MfmBlock()

data class Center(val children: List<MfmInline>) : MfmBlock()

// ---------------------------------------------------------------------------
// Inline nodes
// ---------------------------------------------------------------------------

sealed class MfmInline : MfmNode()

data class MfmText(val text: String) : MfmInline()

data class Bold(val children: List<MfmInline>) : MfmInline()

data class Italic(val children: List<MfmInline>) : MfmInline()

data class Strike(val children: List<MfmInline>) : MfmInline()

data class Small(val children: List<MfmInline>) : MfmInline()

/** `<plain>` タグ — 内側の MFM を無効化する */
data class Plain(val children: List<MfmText>) : MfmInline()

/** `$[name.arg1=val content]` */
data class Fn(
    val name: String,
    val args: Map<String, String?>,
    val children: List<MfmInline>,
) : MfmInline()

data class Link(
    val url: String,
    val children: List<MfmInline>,
    val silent: Boolean,
) : MfmInline()

data class Url(val url: String, val brackets: Boolean) : MfmInline()

data class Mention(
    val username: String,
    val host: String?,
    val acct: String,
) : MfmInline()

data class Hashtag(val hashtag: String) : MfmInline()

data class EmojiCode(val name: String) : MfmInline()

data class UnicodeEmoji(val emoji: String) : MfmInline()

data class InlineCode(val code: String) : MfmInline()

data class MathInline(val formula: String) : MfmInline()

// ---------------------------------------------------------------------------
// Simple nodes (SimpleParser が返すサブセット)
// ---------------------------------------------------------------------------

sealed class MfmSimpleNode : MfmNode()

data class SimpleUnicodeEmoji(val emoji: String) : MfmSimpleNode()

data class SimpleEmojiCode(val name: String) : MfmSimpleNode()

data class SimpleText(val text: String) : MfmSimpleNode()

data class SimplePlain(val children: List<SimpleText>) : MfmSimpleNode()

// ---------------------------------------------------------------------------
// Helper
// ---------------------------------------------------------------------------

fun MfmNode.isMfmBlock(): Boolean = this is MfmBlock
