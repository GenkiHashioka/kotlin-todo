# Phase 4.9 (c) — Konform バリデーション + StatusPages 拡張

**ステータス**: (c) 完了。Phase 4.9 全体が完了し、Phase 4.10（OpenAPI / Swagger UI）に進む
**開始日**: 2026-08-19
**完了日**: 2026-08-22

Phase 4.9 (b) で CRUD API は動くようになったが、手動確認で 3 つの穴が見つかっていた。**400 が返るのにボディが空**、**業務的な検証が無い**、**想定外の例外が裸で 500**。本 PR はこの 3 つを埋め、「不正な入力に正しく応答する API」にするのが目的。

設計は [design-notes/phase-04.9c-konform-and-status-pages.md](../design-notes/phase-04.9c-konform-and-status-pages.md) に事前記録済み（判断 1〜11、懸念 A〜H）。本 journal は**実装して初めて分かったこと**と**設計メモからの乖離**を主に記録する。

## 学習目標

- Konform の DSL 型バリデーションと、Bean Validation のアノテーション方式との違い
- 検証をどの層で実行すべきかを、既存のレイヤー定義から導く
- ライブラリの型を境界で自前の型に詰め替える理由と、その効果の測り方
- Ktor の `install(StatusPages)` でハンドラがどう選ばれるか
- 例外の原因連鎖（`cause`）を安全に辿る方法
- クライアントに返す情報とログに残す情報を意図的に分ける設計

## 成果物

### validation/ パッケージ（新規、Presentation 層）

- `TodoValidation.kt` — `Validation<TodoCreateRequest>` / `Validation<TodoUpdateRequest>` の定義、`validateOrThrow()` 拡張関数、Konform の `ValidationError` → 自前の `FieldError` への変換
- `ValidationException.kt` — `List<FieldError>` を運ぶ `RuntimeException`

**パッケージ外に公開しているのは `validateOrThrow()` の 2 つだけ**。`io.konform.*` の import はこのパッケージにしか現れない。

### dto/error/ パッケージ（新規）

- `ErrorResponse.kt` — `dto/` 直下から移動。`fieldErrors: List<FieldError> = emptyList()` を追加
- `FieldError.kt` — 新規。`field` / `message`

### 既存ファイルの変更

- `backend/build.gradle.kts` — `io.konform:konform-jvm:0.11.1` を追加
- `Application.kt` — StatusPages に 3 ハンドラ追加（`ValidationException` / `BadRequestException` / `Throwable`）
- `routes/TodoRoutes.kt` — POST / PUT に `request.validateOrThrow()` を 1 行ずつ追加、import 更新

### ドキュメント

- [ADR 0016](../decisions/0016-konform-for-validation.md) — Konform 採用、Presentation 層で実行、`validation/` に置く
- [ADR 0017](../decisions/0017-error-response-and-exception-mapping.md) — エラーレスポンスの形と例外 → HTTP 変換
- `docs/architecture.md` を 0.3 に更新（技術スタック、ディレクトリ表、モジュール構成、§5 全面改訂、§7、§9）
- ルート `README.md` — Konform 追加、進捗更新、400 レスポンス例を追加
- `docs/README.md` — (c) 完了

## チェックポイント結果

[#25](https://github.com/GenkiHashioka/kotlin-todo/issues/25) により Testcontainers が起動できず自動テストが実行不能のため、**すべて手動 curl で確認した**。

### 正常系（回帰）

| 確認 | 結果 |
|---|---|
| `POST /todos` 正常なボディ | 201 + `Location` ヘッダ。検証を足しても壊れていない |
| `GET /todos` | 200 |
| `GET /todos/{id}` 存在しない ID | **404**（`exception<Throwable>` を入れた後も飲み込まれていない） |

### Konform の検証失敗 → 400 + `fieldErrors`

| 入力 | 結果 |
|---|---|
| `title: ""` | 400 / `[{"field":"title","message":"must not be blank"}]` |
| `title: "   "`（空白のみ） | 400 / 同上 |
| `description` 2001 文字 | 400 / `must have at most 2000 characters` |
| `categoryId: -1` | 400 / `must be at least '1'`。**404 ではない**（検証が Service より前で走っている） |
| `title: ""` + `description` 2001 文字 | 400 / **`fieldErrors` が 2 件**。Konform が最初の失敗で止まらず全部集めている |

`field` の値がすべて先頭ドット無し（`"title"` であって `".title"` ではない）。

### 必須項目の欠落 → 400 + `fieldErrors`

| 入力 | 結果 |
|---|---|
| `title` を送らない | 400 / `[{"field":"title","message":"is required"}]` |
| `title` と `priority` を送らない | 400 / **2 件** |

**レスポンスに `com.example.kotlin_todo` という文字列が現れないこと**を確認済み。

### 解析不能な入力 → 400 + 汎用メッセージ

| 入力 | 結果 |
|---|---|
| `priority: "URGENT"` | 400 / `"Malformed request body"` / `fieldErrors` 空 |
| `dueDate: "2026/08/31"` | 同上 |
| `-d '{'`（壊れた JSON） | 同上。ログに WARN でスタックトレース |

### 500 のフォールバック

`docker compose stop postgres` の状態でリクエスト。

```
クライアント: {"status":500,"message":"Internal server error","fieldErrors":[]}  （65 バイト）
ログ (ERROR): SQLTransientConnectionException → PSQLException: Connection to localhost:5432 refused → ConnectException
```

**接続先のホスト名もポートもライブラリ名もレスポンスに出ていない。**

### 既存経路への影響

`GET /todos/999999` → `{"status":404,"message":"Todo not found: id=999999","fieldErrors":[]}`

`fieldErrors` が空配列で出ている。デフォルト値を付けた `ErrorResponse` の拡張が後方互換であること、および Ktor の `DefaultJson` が `encodeDefaults = true` であることの両方が同時に確認できた。

## 学んだこと

### 「実物を見てから書く」を手順に組み込む価値

design-note の実装ステップで、**ステップ 2 を「Konform の実 API を確認する」**にしていた。コードを 1 行も書く前に、jar と sources を Maven Central から取って中身を読む工程である。

これが本 PR で最も効いた。0.x のライブラリは記事とバージョンがズレていることが多く、記憶や検索結果を根拠にすると外す。実際、事前に懸念として挙げていた 2 点は**どちらも実物を見た瞬間に解決した**うえ、**記事を読んでいただけでは気づけなかった落とし穴**（`dataPath` の先頭ドット）が新たに見つかった。

(b) で Ktor のソースを読んで 400 の仕組みを確定させたのと同じ進め方が、そのまま再現した。

### Konform は「フィールド名を文字列で書かない」

```kotlin
private val validateTodoCreateRequest = Validation<TodoCreateRequest> {
    TodoCreateRequest::title {
        notBlank()
        maxLength(200)
    }
}
```

`TodoCreateRequest::title` は**プロパティ参照**（`KProperty1`）であって文字列ではない。エラーのパスはこの参照から組み立てられる。

```kotlin
// io/konform/validation/path/PropRef.kt
public data class PropRef(val property: KProperty1<*, *>) : PathSegment {
    override val pathString: String get() = ".${property.name}"
}
```

**フィールドをリネームすると検証定義もコンパイルエラーになる。** Bean Validation の `@NotBlank` はアノテーションなので、フィールドを消しても検証が黙って消えるだけだった。ここは Konform が明確に優れている点。

`Validation<T> { }` の `{ }` が書けるのは、これがコンストラクタではなく `companion object` の `invoke` 演算子で、引数が `ValidationBuilder<T>` をレシーバに持つラムダだから。`TodoCreateRequest::title { ... }` の `{ ... }` の方は `ValidationBuilder<T>` に生えている `KProperty1<T, R>.invoke` 拡張演算子である。**同じ `invoke` という名前が 2 種類出てくるので混同しやすい。**

### `notBlank()` があるので `minLength(1)` は書かない

```kotlin
public fun ValidationBuilder<String>.notBlank(): Constraint<String> =
    constrain("must not be blank") { it.isNotBlank() }
```

`isNotBlank()` は空文字も空白のみも false なので、`minLength(1)` は完全に包含される。

design-note には「1〜200 文字 / 空白のみ不可」と書いていたので、素直に読むと制約 3 つになる。しかし **Konform は失敗した制約をすべて集めて返す**（最初の 1 件で止まらない）ため、両方書くと `title: ""` のときに同じフィールドへ 2 件のエラーが並ぶ。

```json
"fieldErrors": [
  { "field": "title", "message": "must not be blank" },
  { "field": "title", "message": "must have at least 1 characters" }
]
```

**「全部集める」という仕様は、冗長な制約をそのままノイズに変える。** Bean Validation も同様に全件返すが、`@NotBlank` と `@Size(min=1)` を両方書く機会があまり無かったので意識していなかった。

### 境界で詰め替えると、依存の閉じ込めが import 文として目に見える

`ValidationException` に持たせるのを Konform の `List<ValidationError>` ではなく自前の `List<FieldError>` にした。

その結果、`Application.kt` の StatusPages ハンドラはこう書ける。

```kotlin
exception<ValidationException> { call, cause ->
    call.respond(
        HttpStatusCode.BadRequest,
        ErrorResponse(
            status = HttpStatusCode.BadRequest.value,
            message = "Validation failed",
            fieldErrors = cause.fieldErrors,   // そのまま渡せる
        ),
    )
}
```

**`Application.kt` に `io.konform.*` の import が 1 つも要らない。** もし Konform の型を運んでいたら、ここで `dataPath` を触る処理が必要になり、`Application.kt` が Konform に依存していた。

「境界で詰め替える」は抽象的な原則に聞こえるが、**効果が import 文の有無という測定可能な形で現れる**。ライブラリを差し替えるとき書き換える範囲が、grep 一発で分かる。

### 同じ記法でも DTO の向きによって意味が変わる

`ErrorResponse` に `fieldErrors: List<FieldError> = emptyList()` を足したとき、(b) で学んだ「kotlinx はデフォルト値の無いプロパティを必須にする」を思い出したが、**今回は関係なかった**。

| | (b) の `TodoCreateRequest` | (c) の `ErrorResponse` |
|---|---|---|
| 方向 | **受信**（デシリアライズ） | **送信**（シリアライズ） |
| デフォルト値の効果 | JSON にキーが無くても許す | **無関係**（送る側なので必須判定は起きない） |
| 実際に効くのは | kotlinx の必須判定 | **Kotlin のコンパイル**（既存の呼び出し 3 箇所を書き換えずに済む） |

`ErrorResponse` はサーバが作って返すだけの型なので、kotlinx の必須判定は一度も走らない。デフォルト値が効いたのは純粋に Kotlin の言語機能としてであり、**「既存の生成箇所を触らずに拡張できた」ことがコンパイル成功として確認できた**。

一方、JSON に `"fieldErrors": []` が出るかどうかは別の設定に依存していた。

```kotlin
// ktor-serialization-kotlinx-json 3.2.0 / JsonSupport.kt:27
public val DefaultJson: Json = Json {
    encodeDefaults = true    // kotlinx 単体の既定は false
    ...
}
```

**Ktor が kotlinx の既定を上書きしている。** これが `false` だったら 404 のレスポンスに `fieldErrors` キーが現れず、確認項目が成立しなかった。書く前に確認しておいて正解だった。

### 例外の原因連鎖は段数を書かずに辿る

Ktor は**デシリアライズ例外を 2 段に包む**。

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

段数が分かったので `cause.cause.cause` と書けるが、**書かない**。

```kotlin
val missingFields = generateSequence<Throwable>(cause) { it.cause }
    .filterIsInstance<MissingFieldException>()
    .firstOrNull()
    ?.missingFields
```

理由は、この 2 段が **Ktor の実装の詳細**だから。Ktor が包み方を変えたら `cause.cause.cause` は静かに `null` になり、必須項目の欠落が「Malformed request body」に落ちる。**コンパイルは通り、テストも（そもそも動かないので）走らず、curl で気づくまで誰も知らない**タイプの劣化になる。

`generateSequence(seed) { next }` は `next` が `null` を返すまで列を作る。`Throwable.cause` は連鎖の終端で `null` になるので、そのまま「原因の連鎖」を列にできる。`Sequence` は遅延評価なので、見つかった時点で辿るのを止める。

**「段数を知っているから短く書ける」は、たいてい「実装の詳細に依存している」と同義。**

### StatusPages は登録順ではなくクラス階層の近さでハンドラを選ぶ

`exception<Throwable>` を登録すると 404 まで飲み込まれるのではないか、という懸念があった。実装を読むと違った。

```kotlin
// StatusPages.kt
fun findHandlerByValue(cause: Throwable): HandlerFunction? {
    val keys = exceptions.keys.filter { cause.instanceOf(it) }
    if (keys.isEmpty()) return null
    if (keys.size == 1) return exceptions[keys.single()]
    val key = selectNearestParentClass(cause, keys)
    return exceptions[key]
}
```

**該当する登録型を全部集め、複数あれば最も近い親クラスを選ぶ。** 登録先が `MutableMap<KClass<*>, HandlerFunction>` なので、書く順序は影響しない。

二重応答についても同じファイルで手当てされていた。

```kotlin
on(CallFailed) { call, cause ->
    if (call.attributes.contains(statusPageMarker)) return@on
```

一度ハンドラを走らせたら印を付け、二度目は何もしない。**懸念していた 2 点はどちらも Ktor 側で解決済みだった。**

ただし実装を読んだだけでは確信しきれないので、`GET /todos/999999` が 404 を返すことを実際に確認した。読んだ内容と実物が一致した。

### ログのレベルは「誰が対処すべき問題か」で決まる

3 つのハンドラでログの扱いを変えた。

| ハンドラ | ログ | 誰の問題か |
|---|---|---|
| `ValidationException` | 出さない | クライアント。正常動作の一部 |
| `BadRequestException` | `warn` | クライアント。ただし返す情報を削ったので行き先が要る |
| `Throwable` | `error` | **サーバ。人間が調べる必要がある** |

最初は「エラーなんだから全部出せばいい」と考えていたが、それをやると**正常に機能している状態がログ上は障害のように見える**。クライアントが JSON を間違えるたびに ERROR が出るログでは、本物の障害が埋もれる。

`ValidationException` でログを出さなくても `install(CallLogging)` が 400 を返した事実は記録するので、情報が完全に失われるわけではない。

`BadRequestException` の else 側だけ `warn` を出しているのは、**クライアントに返す情報を意図的に削っているから**。`"Malformed request body"` だけでは開発中に自分でも原因が分からない。削った情報の行き先を用意する必要がある。

### 情報の非対称を作るのが目的だった

PostgreSQL を止めて 500 を確認したとき、この PR が何をやったのかがいちばんはっきり見えた。

| 出力先 | 内容 |
|---|---|
| ログ（ERROR） | `localhost:5432`、`HikariPool-1`、`org.postgresql.util.PSQLException`、タイムアウト値、スタックトレース 3 段 |
| クライアント | 65 バイト。`{"status":500,"message":"Internal server error","fieldErrors":[]}` |

もし `cause.message` を流していたら、`Connection to localhost:5432 refused` がそのまま返り、**DB の所在をクライアントに教えていた**。

想定外の例外は「何が入るか想定していない」ものなので、中身を見ずに流すのは内部情報の無検査な公開に等しい。既存の 404 が `cause.message` を使ってよいのは、`TodoNotFoundException` が**我々が書いた例外**で内容を制御しているから。**素性の分かる例外と分からない例外を分けて扱う。**

### 「重複を消す」ではなく「変更の理由が同じものをまとめる」

本 PR で判断が正反対になった箇所が 2 つ並んでいる。

| | 検証ルール（Create / Update） | 詰め替え処理 |
|---|---|---|
| 現状 | 完全に同じ内容 | 1 つにまとめた |
| 同じである理由 | **偶然**。要件が今たまたま一致 | **必然**。Konform の API がひとつ |
| 将来 | PATCH を入れれば分岐する | Konform を使う限り分岐しない |
| 判断 | **重複させる** | **共通化する** |

見た目の重複だけを基準にすると、前者も共通化したくなる。しかし共通化には 2 つの DTO に共通インターフェースを持たせる必要があり、それは (b) で決めた「Create と Update を統合しない」を打ち消す。

**判断基準は「将来これらが独立に変わるか」。** 独立に変わりうるものを共通化すると、変わるときに剥がす作業が発生する。逆に、同じ事実に由来するものを重複させると、その事実が変わったときに直し漏れる。

## design-note からの乖離

### 乖離 1: 懸念 C が的中し、スケッチが間違っていた

design-note の判断 9 にこう書いていた。

```kotlin
val missing = (cause.cause as? MissingFieldException)?.missingFields
```

**1 段しか辿っていない。** 実際は `BadRequestException` → `JsonConvertException` → `MissingFieldException` の 2 段だった。

同時に、懸念 C にはこう書いてあった。

> 判断 9 のスケッチは `cause.cause as? MissingFieldException` としているが、Ktor が例外を包む段数が 1 段とは限らない。
> → **対処**: まず素の状態でログにスタックトレースを出し、実際のネスト構造を目で見てから書く。段数が不定なら `generateSequence(cause) { it.cause }...` のように辿る。

**懸念に書いた対処がそのまま採用された。** design-note に「自信が無い箇所」を明示しておく運用が機能した例で、実装中に慌てずに済んだ。

なお段数の確定はソースを読んで行い、後から出たスタックトレースが同じ構造を示したので二重に裏が取れた。

### 乖離 2: 懸念 A・B は杞憂だったが、別の落とし穴があった

| 懸念 | 事前の想定 | 実際 |
|---|---|---|
| A: `dataPath` か `path` か分からない | どちらかしか無いかも | **両方ある**（`path` が本体、`dataPath` は派生） |
| B: 空白チェックが組み込みに無いかも | `addConstraint` で自前実装が要るかも | **`notBlank()` が存在**。メッセージも想定どおり |

一方、**事前に想定していなかった問題**が見つかった。

```kotlin
override val pathString: String get() = ".${property.name}"
```

`dataPath` は JSONPath 風の表記なので**先頭にドットが付く**。そのまま返すとクライアントには `{"field": ".title"}` と見える。`removePrefix(".")` で落とした。

**「確認が必要」と書いた項目が杞憂だったこと自体は、確認の価値を否定しない。** 確認しに行ったからこそ、想定していなかった方の問題に気づけた。

### 乖離 3: `title` の制約が 3 つではなく 2 つになった

design-note の判断 6 では `title` を「必須 / 空白のみ不可 / 1〜200 文字」と書いていた。素直に実装すると `notBlank()` + `minLength(1)` + `maxLength(200)` の 3 つになる。

実際は `minLength(1)` を落として 2 つにした。理由は上記「`notBlank()` があるので `minLength(1)` は書かない」のとおり。

**設計時は「ルールを日本語で列挙」していたが、実装時は「制約オブジェクトの重複」という別の観点が必要になった。** 日本語の要件と、ライブラリ上の制約は 1 対 1 に対応しない。

## 実運用に関わる既知の課題（今回は対応を見送り）

### DB 接続不可時にリクエストが 30 秒ハングする（[#29](https://github.com/GenkiHashioka/kotlin-todo/issues/29)）

500 のフォールバックを確認する過程で発見した。

```
500 Internal Server Error: GET - /todos in 60248ms
```

HikariCP の `connectionTimeout` が既定の 30 秒で、DB に到達できないとその間スレッドを占有し続ける。**(c) が作った不具合ではなく、それまで空の 500 に埋もれていた挙動が可視化されたもの。**

注意点として、`connectionTimeout` が計っているのは「**プールから接続を借りる待ち時間**」であって、クエリの実行時間ではない。**実行に時間がかかるクエリはこの設定では中断されない**（それを止めるのは `socketTimeout` や `statement_timeout`）。したがって「短くすると本当に遅い処理まで落ちる」という懸念は、この設定については当たらない。落ちるのは裏で接続を待っている別のリクエストの方。

ただしトレードオフは実在する。バーストでプールが枯渇したとき（`maximumPoolSize = 10`）、数秒待てば成功したはずのリクエストを落とすことになる。**何秒にするかは `maximumPoolSize` とセットで決める話**なので、接続情報の外部化と同時に扱う。

### enum 不正値と日付形式エラーの情報が薄い

`"Malformed request body"` だけを返しており、クライアントは何が悪いか分からない。kotlinx の例外からフィールド名を機械的に取り出せないため、現状はログを見られる開発者しか原因にたどり着けない。

改善するなら、`JsonDecodingException` のメッセージをパースするか、DTO の全フィールドを `String` で受けてから自前で変換する形になる。どちらも代償が大きいので見送った。Phase 4.10 で OpenAPI を書けば、少なくとも「何を送るべきか」はクライアント側で分かるようになる。

### エラーメッセージが英語の固定文字列（[#5](https://github.com/GenkiHashioka/kotlin-todo/issues/5)）

Konform に移行したことで、Bean Validation 時代の「`Accept-Language` によって言語が勝手に変わる」問題そのものは消滅した。一方で「何語で返すか」「機械可読なコードを持たせるか」は未決のまま残っている。

(c) では**英語の固定文字列、コードは持たせない**という暫定方針を採った。`FieldError` に `code` を後からデフォルト値付きで追加できるため、見送りのコストは低い。フロントエンド着手時に決める。

### Konform が 0.x である

最新が 0.11.1（2025-03-31）で、まだ 1.0 に達していない。実際に `ValidationError` のパス取得は 0.x の間に形が変わっている。バージョンを上げるときは `validation/TodoValidation.kt` の詰め替え処理を必ず確認する必要がある。

依存が `validation/` パッケージに閉じているので、影響範囲の特定は容易。

### パスパラメータの 400 だけ方式が違う

`GET /todos/abc` のような数値でない ID は、例外ではなく `toLongOrNull() ?: 400` の分岐で処理している（(b) の実装）。**同じファイル内に「分岐して respond」と「例外を投げて StatusPages」の 2 方式が混在している。**

`architecture.md` が「パスパラメータの形式検証」を Presentation 層の責務として明記しているので一貫性は保たれているが、`INVALID_ID_RESPONSE` という `ErrorResponse` の生成が routes に残っているのは事実。例外方式に揃えることもできるが、(c) のスコープ外とした。

### テストが 1 行も書けていない

[#25](https://github.com/GenkiHashioka/kotlin-todo/issues/25) により Testcontainers が起動できず、`./gradlew test` は 9 件全滅のまま。**(c) の検証はすべて手動 curl で行った。**

このため本 journal のチェックポイント結果を厚めに書いた。Phase 4.11 で `testApplication` によるテストを書くとき、ここに並べた入力と期待値をそのまま移植できるようにしてある。

なお `build` タスクは `test` を含むため、(c) の間は `./gradlew compileKotlin` と `./gradlew build -x test` を使い分けた。
