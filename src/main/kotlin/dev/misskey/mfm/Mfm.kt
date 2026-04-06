package dev.misskey.mfm

import dev.misskey.mfm.internal.Util
import dev.misskey.mfm.node.*
import dev.misskey.mfm.parser.FullParser
import dev.misskey.mfm.parser.SimpleParser

/**
 * MFM (Misskey Flavored Markdown) パーサーの公開 API。
 *
 * ```kotlin
 * val nodes = Mfm.parse("**Hello** @user")
 * val text  = Mfm.toString(nodes)
 * ```
 */
object Mfm {

    /**
     * MFM 文字列をフルパーサーで解析し、ノードツリーを返す。
     *
     * @param input     MFM テキスト
     * @param nestLimit ネスト上限 (デフォルト 20)
     */
    fun parse(input: String, nestLimit: Int = 20): List<MfmNode> =
        FullParser.parse(input, nestLimit)

    /**
     * MFM 文字列をシンプルパーサーで解析する。
     * HTML タグや高度な構文は処理しない。
     */
    fun parseSimple(input: String): List<MfmSimpleNode> =
        SimpleParser.parse(input)

    /**
     * ノードツリーを MFM 文字列に変換する。
     */
    fun toString(nodes: List<MfmNode>): String =
        Util.stringifyTree(nodes)

    /**
     * ノードツリーを再帰的に走査し、[action] を各ノードに適用する。
     */
    fun inspect(nodes: List<MfmNode>, action: (MfmNode) -> Unit) =
        nodes.forEach { Util.inspectOne(it, action) }

    /**
     * ノードツリーを走査し、[predicate] が非 null を返すノードを収集して返す。
     */
    fun <T : MfmNode> extract(nodes: List<MfmNode>, predicate: (MfmNode) -> T?): List<T> {
        val result = mutableListOf<T>()
        inspect(nodes) { node -> predicate(node)?.let { result.add(it) } }
        return result
    }
}
