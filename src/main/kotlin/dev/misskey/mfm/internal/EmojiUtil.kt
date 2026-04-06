package dev.misskey.mfm.internal

/**
 * Unicode 絵文字の検出ユーティリティ。
 *
 * Android (API 21+) / JVM Java 8 で動作するように、
 * `\p{Emoji}` 等の Java 9+ Unicode プロパティは使わず
 * コードポイント範囲ベースの正規表現を使用する。
 */
internal object EmojiUtil {

    /**
     * 絵文字シーケンスにマッチする正規表現。
     *
     * 対応範囲:
     * - 国旗 (Regional Indicator ペア)
     * - 絵文字 + 肌色修飾子 (U+1F3FB–1F3FF)
     * - Variation Selector-16 (U+FE0F)
     * - Combining Enclosing Keycap (U+20E3)
     * - ZWJ シーケンス (U+200D)
     * - 一般的な絵文字ブロック
     */
    private val EMOJI_REGEX: Regex = run {
        val ri = "[\uD83C][\uDDE6-\uDDFF]"                  // Regional Indicator (サロゲートペア)
        val modifier = "[\uD83C][\uDFFB-\uDFFF]"             // skin tone modifiers
        val vs16 = "\uFE0F"
        val zwj = "\u200D"
        val keycap = "\u20E3"
        val enclosing = "\u20E0"

        // Miscellaneous Symbols and Pictographs: U+1F300–1F5FF → D83C DC00–D83D DDFF
        val misc = "[\uD83C][\uDC00-\uDFFF]|[\uD83D][\uDC00-\uDDFF]"
        // Emoticons: U+1F600–1F64F → D83D DE00–DE4F
        val emoticons = "[\uD83D][\uDE00-\uDE4F]"
        // Transport and Map: U+1F680–1F6FF → D83D DE80–DEFF
        val transport = "[\uD83D][\uDE80-\uDEFF]"
        // Supplemental Symbols: U+1F900–1F9FF → D83E DD00–DDFF
        val supplemental = "[\uD83E][\uDD00-\uDDFF]"
        // Symbols and Pictographs Extended-A: U+1FA00–1FAFF → D83E DE00–DEFF
        val extA = "[\uD83E][\uDE00-\uDEFF]"
        // Dingbats, Misc Symbols (BMP): U+2600–27BF
        val bmp = "[\u2600-\u27BF]"
        // Enclosed Alphanumeric Supplement: U+1F100–1F1FF → D83C DD00–DDFF (incl. keycap base)
        val enclosed = "[\uD83C][\uDD00-\uDDFF]"
        // ASCII keycap bases: #, *, 0–9
        val keycapBase = "[#*0-9]"

        // 完全なシーケンスパターン (ZWJ チェーン対応)
        val single = "(?:$ri|(?:$misc|$emoticons|$transport|$supplemental|$extA|$bmp|$enclosed)(?:$modifier)?(?:$vs16)?(?:$keycap)?|$keycapBase$vs16$keycap)"
        val pattern = "(?:$single)(?:$zwj(?:$single))*"

        Regex(pattern)
    }

    /** [index] 位置から始まる絵文字シーケンスを返す。なければ null */
    fun matchAt(input: String, index: Int): String? {
        val match = EMOJI_REGEX.find(input, index) ?: return null
        return if (match.range.first == index) match.value else null
    }
}
