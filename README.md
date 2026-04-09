# mfm-kt

A Kotlin port of [mfm.js](https://github.com/misskey-dev/mfm.js) — an MFM (Misskey Flavored Markdown) parser.

> **Acknowledgements**
> This library is based on the design and specification of [mfm.js](https://github.com/misskey-dev/mfm.js) by [misskey-dev](https://github.com/misskey-dev), licensed under the MIT License.
> mfm-kt would not exist without their excellent work.

## Features

- Full MFM parser (`Mfm.parse()`)
- Simple MFM parser (`Mfm.parseSimple()`)
- Serialize AST back to MFM string (`Mfm.toString()`)
- Tree traversal utilities (`Mfm.inspect()`, `Mfm.extract()`)
- Zero external dependencies
- Java 8 bytecode — compatible with Android (API 21+)

## Installation

### Gradle (via JitPack)

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
}
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.github.pantasystem:mfm-kt:v0.2.1")
}
```

## Usage

### Parse

```kotlin
import dev.misskey.mfm.Mfm
import dev.misskey.mfm.node.*

// Full parser
val nodes: List<MfmNode> = Mfm.parse("**Hello** @alice #misskey")

// Simple parser (emoji and plain text only)
val simple: List<MfmSimpleNode> = Mfm.parseSimple(":kawaii:テキスト")
```

### Serialize

```kotlin
val nodes = Mfm.parse("**Hello**")
val text = Mfm.toString(nodes)  // "**Hello**"
```

### Inspect / Extract

```kotlin
// 全ノードを再帰的に走査
Mfm.inspect(nodes) { node ->
    println(node)
}

// 特定の型のノードだけ抽出
val mentions = Mfm.extract(nodes) { it as? Mention }
val emojis   = Mfm.extract(nodes) { it as? EmojiCode }
```

## Node Types

### Block

| Type | Syntax |
|------|--------|
| `Quote` | `> text` |
| `CodeBlock` | ` ```lang\ncode\n``` ` |
| `MathBlock` | `\[formula\]` |
| `Center` | `<center>text</center>` |
| `Search` | `text [検索]` |

### Inline

| Type | Syntax |
|------|--------|
| `Bold` | `**text**` / `<b>text</b>` / `__text__` |
| `Italic` | `*text*` / `<i>text</i>` / `_text_` |
| `Strike` | `~~text~~` / `<s>text</s>` |
| `Small` | `<small>text</small>` |
| `Plain` | `<plain>text</plain>` |
| `InlineCode` | `` `code` `` |
| `MathInline` | `\(formula\)` |
| `Mention` | `@user` / `@user@host` |
| `Hashtag` | `#tag` |
| `EmojiCode` | `:emoji_name:` |
| `UnicodeEmoji` | `😀` |
| `Url` | `https://...` / `<https://...>` |
| `Link` | `[label](url)` / `?[label](url)` |
| `Fn` | `$[name.arg=val content]` |
| `MfmText` | plain text |

## License

MIT License — see [LICENSE](LICENSE)

This project is inspired by and based on the specification of [mfm.js](https://github.com/misskey-dev/mfm.js) (MIT License, © misskey-dev).
