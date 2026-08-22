# 0017 - エラーレスポンスの形を統一し、例外 → HTTP 変換を StatusPages に集約する

**ステータス**: 採用
**日付**: 2026-08-22

## Context（背景・何を解決したいか）

Phase 4.9 (b) の手動確認で、エラー時のレスポンスに 3 つの穴が見つかった。

| # | 症状 | 原因 |
|---|---|---|
| 1 | 不正な入力で **400 が返るがボディが空**（`Content-Length: 0`） | 下記の「2 系統」参照 |
| 2 | 想定外の例外が **500 でボディが空** | 同上 |
| 3 | エラーの形が経路によって違う | 404 だけ `ErrorResponse`、他は空 |

### Ktor には例外を HTTP に変換する経路が 2 系統ある

**系統 A: `install(StatusPages)`** — 明示的に登録した例外だけを処理する。登録していない例外はここを素通りする。

**系統 B: `io.ktor.server.engine.defaultExceptionStatusCode`** — Ktor 組み込みの対応表。

```kotlin
public fun defaultExceptionStatusCode(cause: Throwable): HttpStatusCode? = when (cause) {
    is BadRequestException -> HttpStatusCode.BadRequest
    is NotFoundException -> HttpStatusCode.NotFound
    is UnsupportedMediaTypeException -> HttpStatusCode.UnsupportedMediaType
    is PayloadTooLargeException -> HttpStatusCode.PayloadTooLarge
    is TimeoutException, is TimeoutCancellationException -> HttpStatusCode.GatewayTimeout
    else -> null
}
```

**これが応答するとき呼ばれるのは `call.respond(statusCode)` だけ**である。ボディを組み立てるコードが無い。だから 400 が返るのにボディが空になる。`else -> null` に落ちた例外は 500 になり、これも同じ理由でボディが空になる。

(b) 時点では `TodoNotFoundException` / `CategoryNotFoundException` の 2 つだけを系統 A に登録していたため、その 2 つだけがボディを持ち、他はすべて空だった。

### デシリアライズ失敗が 400 になる仕組み

`priority: "URGENT"` や `title` の欠落は、Ktor が 2 段階で包み直して `BadRequestException` になる。

```kotlin
// KotlinxSerializationConverter.kt:78-79 — 内側
} catch (cause: Throwable) {
    throw JsonConvertException("Illegal input: ${cause.message}", cause)
}

// RequestConverter.kt:68-69 — 外側
} catch (cause: Throwable) {
    throw BadRequestException("Failed to convert request body to ${receiveType.type}", cause)
}
```

結果として、原因の連鎖は次の 3 段になる。

```
BadRequestException
  └─ JsonConvertException
       └─ MissingFieldException / JsonDecodingException など（kotlinx 本体）
```

## Decision（何を決めたか）

### 1. すべてのエラーを `ErrorResponse` の形に統一する

```kotlin
@Serializable
data class ErrorResponse(
    val status: Int,
    val message: String,
    val fieldErrors: List<FieldError> = emptyList(),
)

@Serializable
data class FieldError(
    val field: String,
    val message: String,
)
```

**`fieldErrors` にデフォルト値を付ける**ことが要点。既存の `ErrorResponse` 生成箇所 3 つ（404 のハンドラ 2 つと、パスパラメータ不正時の 400）を一切変更せずにコンパイルが通り、レスポンスには `"fieldErrors": []` が出る。エラーの種類を増やすたびに全ハンドラを触る構造を避けられる。

なお Ktor の `DefaultJson` は `encodeDefaults = true` を設定している（kotlinx 単体の既定は `false`）ため、デフォルト値のままでも JSON に出力される。

クライアントは「エラーなら必ず `{status, message, fieldErrors}` が来る」とだけ知っていればよく、ステータスコードごとに別の形を想定しなくてよい。

### 2. `ErrorResponse` は `dto/error/` に置く

```
dto/
├── CategorySummary.kt
├── TodoCreateRequest.kt
├── TodoResponse.kt
├── TodoUpdateRequest.kt
├── error/
│   ├── ErrorResponse.kt
│   └── FieldError.kt
└── serializer/
```

`TodoResponse` は「`GET /todos/{id}` の戻り値」で特定のエンドポイントに紐づくが、`ErrorResponse` は**どのエンドポイントからも出る**うえ、routes ではなく **StatusPages が生成する**。

分ける根拠は「異常系だから」ではなく **「横断的だから」**。`serializer/` が既に同じ理屈で切られているため、`dto/` 直下 = エンドポイントの表現、サブパッケージ = 横断的な仕組み、という一貫した読み方になる。

正常系 / 異常系や粒度（`summary/` など）による分割は採らない。リソース軸での分割方針は [#28](https://github.com/GenkiHashioka/kotlin-todo/issues/28) に記録。

### 3. 検証失敗は例外を投げて StatusPages に集約する

routes 内で結果を分岐させる書き方（`if (result is Invalid) { call.respond(...); return@post }`）は採らない。

```kotlin
post {
    val request = call.receive<TodoCreateRequest>()
    request.validateOrThrow()          // 失敗なら ValidationException
    val created = todoService.create(...)
}
```

routes は「駄目だった」と投げるだけで、**どのステータスでどんな JSON になるかを知らない**。`ErrorResponse` の組み立てが 1 か所に集まり、POST と PUT で形が食い違う余地が構造的に無くなる。

`ValidationException` が運ぶのは Konform の `List<ValidationError>` ではなく、**我々の `List<FieldError>`** とする。これにより `Application.kt` に `io.konform.*` の import が要らなくなる（[ADR 0016](0016-konform-for-validation.md) の 3 と対）。

### 4. StatusPages に 3 つのハンドラを追加する

| 登録する型 | ステータス | `message` | `fieldErrors` |
|---|---|---|---|
| `ValidationException` | 400 | `"Validation failed"` | Konform のエラー |
| `BadRequestException`（必須項目の欠落） | 400 | `"Validation failed"` | 欠落フィールド名 |
| `BadRequestException`（それ以外） | 400 | `"Malformed request body"` | 空 |
| `Throwable` | 500 | `"Internal server error"` | 空 |

**必須項目の欠落だけを構造化する**。原因の連鎖から `MissingFieldException` を探し、`missingFields: List<String>` をフィールド名として使う。

```kotlin
val missingFields = generateSequence<Throwable>(cause) { it.cause }
    .filterIsInstance<MissingFieldException>()
    .firstOrNull()
    ?.missingFields
```

`cause.cause.cause` と段数を直接書かない。段数は Ktor の実装の詳細であり、変わればコンパイルは通ったまま `null` になって静かに劣化する。`generateSequence` は段数に依存しない。

これにより、`title` を送り忘れたときのレスポンスが Konform の検証失敗と**同じ形**になる。クライアントはエラーの出どころ（kotlinx か Konform か）を意識せずに済む。

**enum 不正値や日付形式エラーは構造化しない**。kotlinx の例外からフィールド名を機械的に取り出せないため、`"Malformed request body"` で返す。

### 5. 例外のメッセージをクライアントに流さない

kotlinx や JDBC の例外メッセージには内部情報が含まれる。

```
Failed to convert request body to class com.example.kotlin_todo.dto.TodoCreateRequest
Connection to localhost:5432 refused. Check that the hostname and port are correct ...
```

パッケージ構成、DB のホスト名とポート、使用ライブラリが漏れる。想定外の例外は「何が入るか想定していない」ものなので、中身を見ずに流すことは内部情報の無検査な公開に等しい。

**クライアントには固定文字列、実物はログへ**。

既存の 404 が `cause.message ?: "Todo not found"` を使っているのは、`TodoNotFoundException` が**我々が書いた例外**で、message の内容（`"Todo not found: id=42"`）を我々が制御しているためである。素性の分からない例外とは扱いを分ける。

### 6. ログのレベルを「誰が対処すべき問題か」で分ける

| ハンドラ | ログ | 理由 |
|---|---|---|
| `ValidationException` | **出さない** | クライアントが直せばよい。正常動作の一部 |
| `BadRequestException` | `warn` | クライアントの問題だが、返す情報を削ったので行き先が要る |
| `Throwable` | `error` | サーバ側の想定外。人間が調べる必要がある |

全部 `error` にすると、クライアントの入力ミスとサーバのバグがログ上で区別できなくなる。検証エラーを `error` で出すと、正常に機能している状態がログ上は障害のように見える。

`ValidationException` でログを出さなくても、`install(CallLogging)` がリクエストとステータスを記録しているため 400 が返った事実は残る。

### 7. エラーメッセージは英語の固定文字列とし、エラーコードは持たせない

[#5](https://github.com/GenkiHashioka/kotlin-todo/issues/5) が挙げる 2 案のうち、案 1（固定文字列）を採り、案 2（`NOT_BLANK` のような機械可読コード）は見送る。

- コードを振るには Konform の全制約に `hint` を手書きする必要があり、本 Phase の目的（空ボディを埋める）に対して労力が釣り合わない
- コードの粒度（制約単位か、フィールド込みか）はフロントエンドの要件で決まる。#5 自身が「FE 着手時に決める」としている

見送りが安全なのは、`FieldError` に `code` を後から**追加**できるため。デフォルト値付きで足せば既存のレスポンスは壊れない。

### 8. 400 と 422 を区別しない

意味論的な検証エラーに `422 Unprocessable Entity` を使う API もあるが、**400 に統一する**。

kotlinx のデシリアライズ失敗が既に 400 を返しているため、Konform の失敗を 422 にすると、クライアントは「入力が悪い」を表す 2 つのコードを扱うことになる。`title` を送り忘れれば 400、空文字で送れば 422 という区別は、クライアントから見て意味のある違いではない。

## Consequences（この決定によって何が得られ、何を犠牲にしたか）

### 得られるもの

- **エラーの形が 1 つになる**: 400 / 404 / 500 のすべてが `{status, message, fieldErrors}`。クライアントの分岐が減る
- **内部情報が漏れない**: パッケージ名、DB のホスト名、ライブラリ名がレスポンスに現れない。PostgreSQL を停止して確認済み（クライアントには 65 バイト、ログには接続文字列を含む完全なスタックトレース）
- **routes に HTTP の詳細が散らない**: `HttpStatusCode` と `ErrorResponse` が routes からほぼ消えた
- **エラー種別の追加が局所的**: 新しい例外を作ったら `Application.kt` に 1 ブロック足すだけ
- **具体型が優先されることを確認済み**: `exception<Throwable>` を登録しても 404 は飲み込まれない。StatusPages は `selectNearestParentClass` で最も近い親クラスを選ぶため、登録順にも依存しない
- **調査に必要な情報はログに残る**: クライアントに返す情報を削った分、`warn` / `error` で完全な原因を記録している

### 犠牲にするもの

- **enum 不正値と日付形式エラーの情報が薄い**: `"Malformed request body"` だけでは、クライアントは何が悪いか分からない。ログを見られる開発者しか原因にたどり着けない
- **多言語対応が無い**: 英語の固定文字列。日本語のフロントエンドを作るなら、表示文言は FE 側で持つことになる
- **`exception<Throwable>` の広さ**: 想定外を全部 500 にするため、本来 400 相当の例外を新しく作っても登録し忘れると 500 になる。登録漏れがステータスコードの誤りとして表面化する
- **`generateSequence` による探索のコスト**: 原因の連鎖を毎回辿る。エラー時のみ、かつ数段なので実測上の問題は無いが、素の `cause.cause` より遅い
- **`ErrorResponse` の変更が全経路に波及しうる**: 形を 1 つにした裏返しとして、フィールドを増やすと全ハンドラのレスポンスに現れる

### 代替案として検討したもの

- **`message` にすべて詰め込む**（`"title must not be blank, description too long"`）: `fieldErrors` を持たせずに済み、DTO が 1 つ減る。ただしクライアントが文字列を解析する羽目になり、フロントエンドで入力欄とエラーを対応付けられない。却下
- **エラー種別ごとに別の DTO を返す**: 検証エラーは `ValidationErrorResponse`、それ以外は `ErrorResponse`、のように分ける案。型としては正確だが、クライアントがステータスコードを見て型を切り替える必要がある。「エラーなら必ずこの形」の単純さを優先し、却下
- **`kotlinx` の例外メッセージをそのまま返す**: 実装が最も簡単で、開発中は情報量も多い。ただし内部のパッケージ構成が漏れ、ライブラリのバージョンで文面が変わる。却下
- **`exception<Throwable>` を置かない**: 想定外は Ktor 既定の 500（空ボディ）に任せる案。ボディの形が経路によって違う状態が残るため却下。ただし「握りつぶしを避けたい」という観点では一理あり、`error` ログを必ず出すことで折り合いをつけた
- **routes 内で分岐して `respond` する**: 例外を使わない案。制御フローが読みやすいという利点はあるが、`ErrorResponse` の組み立てが呼び出し箇所の数だけ増える。既に `TodoNotFoundException` が StatusPages 経由になっているため、2 つの方式が混在することにもなる。却下（ただしパスパラメータの形式検証は (b) の実装を維持しており、結果的に混在は残っている）
- **`422 Unprocessable Entity` を使う**: 上記 8 のとおり却下

## 関連

- [ADR 0016 - 入力バリデーションに Konform を使い、Presentation 層で実行する](0016-konform-for-validation.md) — 検証を実行する側
- [ADR 0006 - DELETE 成功時は 204 ではなく 200+削除内容を返す](0006-delete-returns-200-with-body.md) — 正常系のレスポンス方針
- [#5](https://github.com/GenkiHashioka/kotlin-todo/issues/5) — エラーメッセージの言語 / コード化（FE 着手時に決定）
- [#28](https://github.com/GenkiHashioka/kotlin-todo/issues/28) — `dto/` のリソース軸分割（Phase 5）
- `backend/src/main/kotlin/com/example/kotlin_todo/Application.kt` — StatusPages の登録
- `backend/src/main/kotlin/com/example/kotlin_todo/dto/error/*.kt` — レスポンス DTO
- `docs/journal/phase-04.9b-ktor-routing-and-dto.md` — 「2 系統」の発見の記録
- `docs/design-notes/phase-04.9c-konform-and-status-pages.md` — 実装前の設計メモ
