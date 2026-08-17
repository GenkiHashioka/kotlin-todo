# Phase 4.9 (b) — Ktor Routing + DTO

**ステータス**: (b) 完了。Phase 4.9 (c)（Konform バリデーション + StatusPages 拡張）に進む
**開始日**: 2026-08-16
**完了日**: 2026-08-17

Spring MVC から Ktor への移行で最後まで残っていた「HTTP を受けて業務ロジックに繋ぐ層」を実装した。Phase 4.8 (b) で Repository + Service まで揃っていたので、その上に Routing と DTO を載せて **CRUD API を再び動く状態に戻す**のが本 PR の目的。

設計は [design-notes/phase-04.9b-ktor-routing-and-dto.md](../design-notes/phase-04.9b-ktor-routing-and-dto.md) に事前記録済み（判断 1〜10、懸念 A〜H）。本 journal は**実装して初めて分かったこと**と**設計メモからの乖離**を主に記録する。

## 学習目標

- kotlinx.serialization の仕組み（コンパイル時コード生成）と Jackson（実行時リフレクション）との違い
- `KSerializer` を自前実装して `java.time` 型を JSON に載せる
- `@file:UseSerializers` によるファイル単位の serializer 適用
- Ktor 3.x の Routing DSL（`Route` 拡張関数、`RoutingContext`、`call.receive` / `call.respond`）
- `install(StatusPages)` による例外 → HTTP ステータス変換
- 手動 DI（composition root）で依存を組み立てる
- ドメインクラスと DTO を分離する理由、および DTO がどの層に属するか

## 成果物

### dto/ パッケージ（新規、Presentation 層）

- `dto/TodoCreateRequest.kt` — 作成リクエストボディ
- `dto/TodoUpdateRequest.kt` — 更新リクエストボディ（現時点では Create と同じ形）
- `dto/TodoResponse.kt` — レスポンスボディ + `Todo.toResponse(category: Category?)` 拡張関数
- `dto/CategorySummary.kt` — `TodoResponse` に入れ子で載せる Category + `Category.toSummary()`
- `dto/ErrorResponse.kt` — `status` + `message` の 2 フィールド
- `dto/serializer/LocalDateSerializer.kt` — `LocalDate` ↔ ISO-8601 文字列
- `dto/serializer/LocalDateTimeSerializer.kt` — `LocalDateTime` ↔ ISO-8601 文字列

### routes/ パッケージ（新規、Presentation 層）

- `routes/TodoRoutes.kt` — `Route.todoRoutes(todoService, devUserId)`
  - `GET /todos`, `GET /todos/{id}`, `POST /todos`, `PUT /todos/{id}`, `DELETE /todos/{id}`
  - `private suspend fun TodoService.buildResponse(todo: Todo): TodoResponse` — Category を引いて `TodoResponse` に組み立てる private 拡張関数

### dev/ パッケージ（新規、暫定コード）

- `dev/DevDataInitializer.kt` — 認証未実装の間 `ownerId` に入れる固定ユーザーを**冪等に**用意する。認証実装時に**パッケージごと削除**する（[#23](https://github.com/GenkiHashioka/kotlin-todo/issues/23)）

### 既存ファイルの変更

- `Application.kt` — `ContentNegotiation` / `CallLogging` / `StatusPages` の install、手動 DI の組み立て、`routing { }` への `todoRoutes` 登録
- `service/TodoService.kt` — `suspend fun findCategoryById(id: Long): Category?` を追加（後述の乖離 1）

### ドキュメント

- `docs/architecture.md` — レイヤー構成の説明を全面補強、モジュール構成を実態に更新、エラー処理フローを実測結果に修正、技術スタックの版数訂正
- 本 journal

## チェックポイント結果

- `./gradlew compileKotlin` **成功**
- `./gradlew run` **成功**。起動ログで Flyway migration と開発用ユーザーの作成を確認
- 2 回目の起動で「開発用ユーザーを再利用します」に変わることを確認（**冪等性 OK**）
- 手動 curl（正常系）— 5 エンドポイントすべて期待通り
  - `POST /todos` → 201 + `Location: /todos/{id}`
  - `dueDate` が **`"2026-08-31"` の文字列**で入出力されることを確認（`LocalDateSerializer` が効いている証拠）
  - `DELETE /todos/{id}` → 200 + 削除した内容が body に入る（ADR 0006）
- 手動 curl（異常系）— 期待通り
  - `GET/PUT/DELETE /todos/9999` → 404 + `ErrorResponse`
  - `POST /todos` で `categoryId: 9999` → 404 + `ErrorResponse`
  - `GET /todos/abc` → 400 + `Invalid id`
- `./gradlew test` **実行不能**（環境要因、後述の既知の課題）

## 学んだこと

### kotlinx.serialization は「実行時に調べる」のではなく「コンパイル時に作る」

Jackson は実行時にリフレクションでクラスを調べ、フィールド名とゲッターを探して JSON にマッピングする。kotlinx.serialization は違い、`@Serializable` を付けると **Kotlin コンパイラプラグインがビルド時に serializer のコードを生成**する。

この違いが実感として出たのが**型の不一致がコンパイルエラーになる**点。`TodoResponse` に serializer の無い型（`LocalDate` など）を置くと、実行してみるまでもなくビルドが止まる。Jackson なら実行時に例外が出るまで気付けない。

ただし**万能ではない**。プロパティ名の綴り間違いは検出できなかった。実装中に `status` を `staus` と書いたが、これは何のエラーにもならずビルドが通った。**プロパティ名は「参照」ではなく「JSON のキーになるデータ」**なので、コンパイラには正誤の判断材料が無い。型は守られるが名前は守られない。

### `KSerializer` は 3 つの部品でできている

`java.time.LocalDate` は kotlinx.serialization が標準では扱えないので自前実装した。

```kotlin
object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.time.LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeString(value.toString())
    }
    override fun deserialize(decoder: Decoder): LocalDate =
        LocalDate.parse(decoder.decodeString())
}
```

- `descriptor` — 「この型は JSON 上でどういう形をしているか」のメタ情報。`PrimitiveKind.STRING` なので単一の文字列
- `serialize` — Kotlin の値 → 出力
- `deserialize` — 入力 → Kotlin の値

重要なのが **`Encoder` / `Decoder` という抽象**。`encoder.encodeString(...)` としか書いていないので、このコードは JSON 専用ではない。同じ serializer が CBOR や ProtoBuf でも使える。**「文字列として書け」とだけ言い、どう書くかはフォーマット側が決める**という分業になっている。

`LocalDate.parse` / `LocalDateTime.parse` が既定で ISO-8601 を読み書きするので、`toString()` と `parse` を往復させるだけで済んだ。`DateTimeFormatter` は不要。

### `@file:UseSerializers` は「型」に紐づく

各プロパティに `@Serializable(with = LocalDateSerializer::class)` を書くと冗長なので、ファイル先頭でまとめて指定した。

```kotlin
@file:UseSerializers(LocalDateSerializer::class, LocalDateTimeSerializer::class)

package com.example.kotlin_todo.dto
```

- **`package` 宣言より前**に書く（file-level use-site target のため）
- 紐づく単位は**フィールド名ではなく型**。`LocalDate` 型のプロパティが何個あっても全部に効く
- **nullable にも効く**。`LocalDate?` に対して別途指定は要らない

### kotlinx はデフォルト値の無いプロパティを必須にする（nullable でも）

Jackson の感覚だと `val description: String?` は「無ければ null」だが、kotlinx.serialization は違う。**デフォルト値が無ければ、たとえ nullable でも JSON にキーが存在しないと `MissingFieldException`** になる。

`= null` を付けて初めて省略可能になる。「nullable であること」と「省略可能であること」が別物として扱われている。

### Ktor には HTTP ステータス変換の経路が 2 つある（本 PR 最大の発見）

design-note では「enum の不正値や必須項目欠落は 500 になり、(c) で 400 に直す」と予測していた。**実際に試したら既に 400 が返った。** ソースを追って理由を確認した。

**経路 1: `install(StatusPages)`**

自分で登録した例外だけを拾う。`TodoNotFoundException` はここに書いたから 404 になっている。

**経路 2: Ktor エンジンの既定処理** — `io.ktor.server.engine.DefaultEnginePipeline`

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

`else -> null` に落ちたものが 500 になる。そして `call.receive<T>()` の失敗がここに乗る理由が ContentNegotiation の実装にあった。

```kotlin
// io/ktor/server/plugins/contentnegotiation/RequestConverter.kt
val convertedBody = try {
    converter.deserialize(charsets, receiveType, body)
} catch (cause: Throwable) {
    throw BadRequestException("Failed to convert request body to ${receiveType.type}", cause)
}
```

`catch (cause: Throwable)` で**デシリアライズ中の例外を何であれ `BadRequestException` に包み直している**。だから kotlinx の `SerializationException` も `MissingFieldException` も 400 になる。

ただし応答の作り方が `call.respond(statusCode)` — **ステータスコードだけでボディが無い**。実際 `Content-Length: 0` が返ってきた。

**まとめると**: 自作例外は StatusPages に登録しないと 500 になる。Ktor 標準例外は何も書かなくてもステータスは正しくなるが、**ボディは空のまま**。

### レイヤー図は「ディレクトリ一覧」ではない

`architecture.md` の mermaid 図に `routes/` はあるのに `dto/` が無く、「DTO はどの層なのか」が読み取れないという問題があった。

原因は**図が描いているのが「実行時に誰が誰を呼ぶか」だから**。`dto/` `domain/` `exception/` は層の間を**運ばれる側**であって呼び出す主体ではないので、ノードにならない。図に無い = 層に属さない、ではない。

`architecture.md` に判定基準（**能動的に他の層を呼び出すか**）と全ディレクトリの対応表を明記して解消した。

### 手動 DI は「依存の向きが目で見える」

`Application.module()` で上から順に生成して渡すだけ。

```kotlin
val categoryRepository = CategoryRepository()
val todoRepository = TodoRepository()
val todoService = TodoService(
    categoryRepository = categoryRepository,
    todoRepository = todoRepository,
)
```

Spring の `@Autowired` と違い、**誰が誰に依存しているかがこの数行を読むだけで分かる**。逆に依存が増えたときの記述量は増えるので、そのコストが上回るまでは手動で進める（[ADR 0014](../decisions/0014-manual-di-over-koin.md)）。

### `runBlocking` は起動時だけの橋渡し

`DevDataInitializer.ensureDevUser` は `suspend fun` だが、`Application.module()` は `suspend` ではない。ここだけ `runBlocking` で繋いだ。

```kotlin
val devUserId = runBlocking { DevDataInitializer.ensureDevUser(userRepository).id }
```

**リクエスト処理中に `runBlocking` を使うとスレッドを塞いで台無しになる**が、起動時の 1 回だけなら問題ない。「どこで coroutine の世界に入るか」を意識する良い例になった。

### 冪等な find-or-create と、そこに潜む競合

`ensureDevUser` は「探して、無ければ作る」。起動のたびにユーザーが増えないようにするためで、ログを 2 種類に分けたので動作が目で確認できた。

厳密には「探した後・作る前」に別プロセスが作ると重複しうる（TOCTOU）。ただし `Users.email` に `uniqueIndex()` があるので **DB 側で弾かれる**。アプリのロジックだけで守ろうとせず、制約に守らせるのが正しい形。

## design-note からの乖離

design-note は**実装前の想定**であり、実装後に更新しない運用（書き捨て）。実際との差分をここに残す。

### 乖離 1: 懸念 E — Routing への `categoryRepository` 直接注入をやめた

**design-note の想定**: `TodoResponse` に Category を載せるため、`todoRoutes` に `categoryRepository` を渡す。

**実際**: `TodoService.findCategoryById(id)` を追加し、Routing からは Service 経由で取得する形にした。

**理由**: `dto/` を Presentation 層と位置づけて「Service / Repository は dto を import しない」と決めた直後に、Presentation から Repository を直接呼ぶのは**同じ原則を逆向きに破ること**になる。`architecture.md` の「依存は上→下のみ、逆流なし」とも整合しない。Service に薄い委譲メソッドを 1 つ足すコストの方が小さいと判断した。

### 乖離 2: §5 異常系表 — 不正な入力は既に 400 だった

**design-note の想定**: enum の不正値（`"priority":"URGENT"`）と必須項目欠落は **500** を返し、(c) で 400 に直す。

**実際**: **どちらも 400 が返った。ただしボディが空**（`Content-Length: 0`）。

**影響**: Phase 4.9 (c) のスコープが変わる。「ステータスコードを直す」作業は不要で、**`exception<BadRequestException>` を StatusPages に登録して空のボディを `ErrorResponse` で埋める**のが主眼になる。Konform による業務ルール検証（title 空文字、長さ上限など）は当初の想定どおり必要。

### 乖離 3: 実装ステップ 11「既存テストが緑のまま」を確認できなかった

環境要因でテストが 1 件も実行できなかった（後述）。テストソースは 1 文字も変更していないため本 PR による regression ではないが、**「緑を確認した」とは言えない**状態で PR を出している。

## 実運用に関わる既知の課題（今回は対応を見送り）

### Testcontainers が Docker Engine 29 に接続できずテストが全滅

**症状**: `./gradlew test` が 9 件すべて失敗。`AbstractPostgresTest` の `companion object` 初期化（コンテナ起動）で `IllegalStateException: Could not find a valid Docker environment`。

**原因**: Docker Engine 29 が **API バージョン 1.40 未満のクライアントを拒否**するようになった（`docker version` の `MinAPI 1.40`）。Testcontainers 内部の docker-java は **v1.32** で接続しにいくため 400 が返る。ソケットに直接投げて確認した結果が以下。

| リクエスト | 結果 |
|---|---|
| `/v1.32/info` | **400** ← Testcontainers はここ |
| `/v1.41/info` | 200 |
| `/info`（バージョン無し） | 200 |

400 応答の本文が中身の無い JSON（`Labels` に `com.docker.desktop.address` だけ）なので、docker-java がこれを「Docker が見つからない」と解釈していた。

**試して効かなかった対処**:

| 試したこと | 結果 |
|---|---|
| Testcontainers `1.20.4` → `1.21.3`（Maven Central 最新） | 同じ 400 |
| `DOCKER_API_VERSION=1.44` 環境変数 | 効かない |
| `TESTCONTAINERS_API_VERSION=1.44` | 効かない |
| `~/.testcontainers.properties` に `api.version=1.44` | 効かない |
| `docker-cli.sock` に向ける | 404（別系統の API） |

API バージョンが Testcontainers 内部に埋め込まれており、外から差し替えられない。**upstream に修正が来るまで待つ**方針とした。`docker compose` 経由の開発用 PostgreSQL とアプリ本体は正常に動作するため、開発そのものは止まらない。

### 一覧取得の N+1（[#3](https://github.com/GenkiHashioka/kotlin-todo/issues/3)）

`GET /todos` は Todo 一覧を 1 回引いた後、`buildResponse` が Todo ごとに Category を 1 件ずつ引く。Todo が N 件なら SELECT が N+1 回。

Exposed には JPA のような永続化コンテキストのキャッシュが無いので、**同じ Category を参照する Todo が複数あっても毎回 SQL が飛ぶ**。JPA 時代（Phase 4）と同じ問題が、原因を変えて再発した形。Phase 5 で JOIN による一括取得に置き換える。

### `dev/` パッケージは暫定コード（[#23](https://github.com/GenkiHashioka/kotlin-todo/issues/23)）

認証が無いため `ownerId` に入れる値が無く、固定ユーザーを作って回避している。パスワードハッシュも `"NOT_A_REAL_HASH_DEV_ONLY"` というプレースホルダ。**認証実装時に `dev/` ごと削除**し、`ownerId` は認証情報から取得する。`TodoRoutes.kt` と `Application.kt` の該当箇所に `TODO(#23)` を残した。

### エラーレスポンスのボディが空になるケースがある

不正な JSON に対する 400 でボディが返らない（前述）。クライアントは「何が悪かったか」を知る手段が無い。Phase 4.9 (c) で解消する。

### `TodoUpdateRequest` が `TodoCreateRequest` と同じ形

現時点で両者は同一のフィールド構成。共通化せず別クラスにしたのは、**更新固有の要件（部分更新、楽観ロック用のバージョン番号など）が入った時に片方だけ変えられるようにする**ため。今は重複に見えるが、意図的な重複として残す。
