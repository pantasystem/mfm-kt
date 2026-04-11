package dev.misskey.mfm.internal

/**
 * Unicode 絵文字の検出ユーティリティ。
 *
 * Android (API 21+) / JVM Java 8 で動作するように、
 * `\p{Emoji}` 等の Java 9+ Unicode プロパティは使わず
 * String.codePointAt() によるコードポイントベースの検出を使用する。
 *
 * 正規表現のサロゲートペア文字クラスは JVM によっては
 * 正しく動作しないため、コードポイントを直接操作する。
 */
internal object EmojiUtil {

    private const val VS16 = 0xFE0F   // Variation Selector-16
    private const val ZWJ  = 0x200D   // Zero Width Joiner
    private const val KEYCAP = 0x20E3 // Combining Enclosing Keycap

    /** ベース絵文字のコードポイント範囲 */
    private fun isBaseEmoji(cp: Int): Boolean = when {
        // Regional Indicator Symbols (国旗用): U+1F1E0–1F1FF
        cp in 0x1F1E0..0x1F1FF -> true
        // Enclosed Alphanumeric Supplement: U+1F100–1F1FF
        cp in 0x1F100..0x1F1FF -> true
        // Enclosed Ideographic Supplement: U+1F200–1F2FF
        cp in 0x1F200..0x1F2FF -> true
        // Miscellaneous Symbols and Pictographs: U+1F300–1F5FF
        cp in 0x1F300..0x1F5FF -> true
        // Emoticons: U+1F600–1F64F
        cp in 0x1F600..0x1F64F -> true
        // Ornamental Dingbats: U+1F650–1F67F
        cp in 0x1F650..0x1F67F -> true
        // Transport and Map Symbols: U+1F680–1F6FF
        cp in 0x1F680..0x1F6FF -> true
        // Alchemical Symbols: U+1F700–1F77F
        cp in 0x1F700..0x1F77F -> true
        // Geometric Shapes Extended (🟦 etc.): U+1F780–1F7FF
        cp in 0x1F780..0x1F7FF -> true
        // Supplemental Arrows-C: U+1F800–1F8FF
        cp in 0x1F800..0x1F8FF -> true
        // Supplemental Symbols and Pictographs: U+1F900–1F9FF
        cp in 0x1F900..0x1F9FF -> true
        // Chess Symbols / Symbols Extended-A: U+1FA00–1FAFF
        cp in 0x1FA00..0x1FAFF -> true
        // Miscellaneous Symbols (BMP): U+2600–26FF
        cp in 0x2600..0x26FF -> true
        // Dingbats (BMP): U+2700–27BF
        cp in 0x2700..0x27BF -> true
        // Miscellaneous Technical (一部): U+2300–23FF
        cp in 0x2300..0x23FF -> true
        // Geometric Shapes (BMP): U+25A0–25FF
        cp in 0x25A0..0x25FF -> true
        // Arrows (BMP): U+2190–21FF
        cp in 0x2190..0x21FF -> true
        // Copyright / Registered signs
        cp == 0x00A9 || cp == 0x00AE -> true
        // !! / !? emoticons
        cp == 0x203C || cp == 0x2049 -> true
        // Mahjong / Domino etc: U+1F000–1F0FF
        cp in 0x1F000..0x1F0FF -> true
        else -> false
    }

    private fun isSkintone(cp: Int): Boolean = cp in 0x1F3FB..0x1F3FF

    /** [index] 位置から始まる絵文字シーケンスを返す。なければ null */
    fun matchAt(input: String, index: Int): String? {
        if (index >= input.length) return null

        // キーキャップシーケンス: [#*0-9] + VS16 + KEYCAP
        val firstCp = input.codePointAt(index)
        val firstLen = Character.charCount(firstCp)
        if (firstCp == '#'.code || firstCp == '*'.code || firstCp in 0x30..0x39) {
            val next = index + firstLen
            if (next + 1 < input.length &&
                input[next].code == VS16 &&
                input[next + 1].code == KEYCAP) {
                return input.substring(index, next + 2)
            }
            // 単独の数字/記号はキーキャップ以外では絵文字にしない
            return null
        }

        if (!isBaseEmoji(firstCp)) return null

        var cur = index + firstLen

        // 肌色修飾子 (skin tone modifier)
        if (cur < input.length && isSkintone(input.codePointAt(cur))) {
            cur += Character.charCount(input.codePointAt(cur))
        }

        // VS16
        if (cur < input.length && input.codePointAt(cur) == VS16) {
            cur += 1  // VS16 は BMP なので常に 1 char
        }

        // ZWJ シーケンス (family/couple emojis など)
        while (cur + 1 < input.length && input.codePointAt(cur) == ZWJ) {
            cur += 1  // ZWJ は BMP (1 char)
            if (cur >= input.length) break
            val nextCp = input.codePointAt(cur)
            if (!isBaseEmoji(nextCp) && !isSkintone(nextCp)) break
            cur += Character.charCount(nextCp)
            // 各セグメント後の肌色・VS16
            if (cur < input.length && isSkintone(input.codePointAt(cur)))
                cur += Character.charCount(input.codePointAt(cur))
            if (cur < input.length && input.codePointAt(cur) == VS16)
                cur += 1
        }

        return if (cur > index) input.substring(index, cur) else null
    }
}
