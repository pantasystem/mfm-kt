package dev.misskey.mfm.parser.core

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

data class State(
    val nestLimit: Int = 20,
    var depth: Int = 0,
    var linkLabel: Boolean = false,
    val trace: Boolean = false,
)

// ---------------------------------------------------------------------------
// Result
// ---------------------------------------------------------------------------

sealed class ParseResult<out T>

data class Success<T>(val value: T, val index: Int) : ParseResult<T>()

object Failure : ParseResult<Nothing>()

// ---------------------------------------------------------------------------
// Parser
// ---------------------------------------------------------------------------

class Parser<T>(val handler: (input: String, index: Int, state: State) -> ParseResult<T>) {

    fun parse(input: String, index: Int, state: State): ParseResult<T> {
        if (state.trace) println("[parse] index=$index input=${input.drop(index).take(20)}")
        return handler(input, index, state)
    }

    fun <U> map(fn: (T) -> U): Parser<U> = Parser { input, index, state ->
        when (val r = parse(input, index, state)) {
            is Success -> Success(fn(r.value), r.index)
            is Failure -> Failure
        }
    }

    /** 消費した入力文字列そのものを返す */
    fun text(): Parser<String> = Parser { input, index, state ->
        when (val r = parse(input, index, state)) {
            is Success -> Success(input.substring(index, r.index), r.index)
            is Failure -> Failure
        }
    }

    fun many(min: Int = 0): Parser<List<T>> = Parser { input, index, state ->
        val results = mutableListOf<T>()
        var cur = index
        while (true) {
            when (val r = parse(input, cur, state)) {
                is Success -> {
                    if (r.index == cur) break  // 無限ループ防止
                    results.add(r.value)
                    cur = r.index
                }
                is Failure -> break
            }
        }
        if (results.size < min) Failure else Success(results, cur)
    }

    fun option(): Parser<T?> = alt(this.map { it as T? }, succeeded<T?>(null))

    fun <U> flatMap(fn: (T) -> Parser<U>): Parser<U> = Parser { input, index, state ->
        when (val r = parse(input, index, state)) {
            is Success -> fn(r.value).parse(input, r.index, state)
            is Failure -> Failure
        }
    }
}

// ---------------------------------------------------------------------------
// Factory functions
// ---------------------------------------------------------------------------

fun str(value: String): Parser<String> = Parser { input, index, _ ->
    if (input.startsWith(value, index)) Success(value, index + value.length)
    else Failure
}

fun regexp(pattern: Regex): Parser<MatchResult> = Parser { input, index, _ ->
    val match = pattern.find(input, index)
    if (match != null && match.range.first == index) Success(match, match.range.last + 1)
    else Failure
}

/** 複数のパーサーを順に適用し、select 番目の結果を返す */
@Suppress("UNCHECKED_CAST")
fun <T> seqOf(vararg parsers: Parser<*>, select: Int): Parser<T> = Parser { input, index, state ->
    var cur = index
    val results = arrayOfNulls<Any>(parsers.size)
    for ((i, p) in parsers.withIndex()) {
        when (val r = p.parse(input, cur, state)) {
            is Success -> { results[i] = r.value; cur = r.index }
            is Failure -> return@Parser Failure
        }
    }
    Success(results[select] as T, cur)
}

/** 複数のパーサーを順に適用し、全結果を List で返す */
fun seqAll(vararg parsers: Parser<*>): Parser<List<Any?>> = Parser { input, index, state ->
    var cur = index
    val results = mutableListOf<Any?>()
    for (p in parsers) {
        when (val r = p.parse(input, cur, state)) {
            is Success -> { results.add(r.value); cur = r.index }
            is Failure -> return@Parser Failure
        }
    }
    Success(results, cur)
}

fun <T> alt(vararg parsers: Parser<out T>): Parser<T> = Parser { input, index, state ->
    for (p in parsers) {
        val r = p.parse(input, index, state)
        if (r is Success) {
            @Suppress("UNCHECKED_CAST")
            return@Parser r as ParseResult<T>
        }
    }
    Failure
}

fun <T> succeeded(value: T): Parser<T> = Parser { _, index, _ -> Success(value, index) }

fun notMatch(parser: Parser<*>): Parser<Unit> = Parser { input, index, state ->
    when (parser.parse(input, index, state)) {
        is Success -> Failure
        is Failure -> Success(Unit, index)
    }
}

fun <T> lazy(fn: () -> Parser<T>): Parser<T> {
    var inner: Parser<T>? = null
    return Parser { input, index, state ->
        (inner ?: fn().also { inner = it }).parse(input, index, state)
    }
}

// ---------------------------------------------------------------------------
// Predefined parsers
// ---------------------------------------------------------------------------

val CR: Parser<String> = str("\r")
val LF: Parser<String> = str("\n")
val CRLF: Parser<String> = str("\r\n")
val NEWLINE: Parser<String> = alt(CRLF, CR, LF)
val CHAR: Parser<String> = Parser { input, index, _ ->
    if (index < input.length) Success(input[index].toString(), index + 1)
    else Failure
}
val LINE_BEGIN: Parser<Unit> = Parser { input, index, _ ->
    if (index == 0 || input[index - 1] == '\n' || input[index - 1] == '\r') Success(Unit, index)
    else Failure
}
val LINE_END: Parser<Unit> = Parser { input, index, _ ->
    if (index >= input.length || input[index] == '\n' || input[index] == '\r') Success(Unit, index)
    else Failure
}
val EOF: Parser<Unit> = Parser { input, index, _ ->
    if (index >= input.length) Success(Unit, index) else Failure
}

// ---------------------------------------------------------------------------
// createLanguage — 相互再帰文法の構築ユーティリティ
// ---------------------------------------------------------------------------

class Language<R>(private val rules: Map<String, Parser<*>>) {
    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(name: String): Parser<T> =
        rules[name] as? Parser<T> ?: error("Unknown rule: $name")
}

fun <R> createLanguage(defs: Map<String, (Language<R>) -> Parser<*>>): Language<R> {
    val lazyMap = mutableMapOf<String, Parser<*>>()
    val lang = Language<R>(lazyMap)
    for ((name, fn) in defs) {
        lazyMap[name] = lazy { fn(lang) }
    }
    return lang
}
