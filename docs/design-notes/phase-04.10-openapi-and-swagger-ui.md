# 詳細設計メモ - Phase 4.10: OpenAPI 仕様の自動生成と Swagger UI

**バージョン**: 0.1
**最終更新**: 2026-08-24
**対応 Phase**: Phase 4.10

## 1. 背景

Spring Boot 時代、API 仕様書は `springdoc-openapi` が `@RestController` から自動生成していた。`docs/api/README.md` はその利点をこう記録している。

> 手書きではなく実装から生成されるため、コードとの乖離が起きない

**Ktor 移行でこれが失われた。** 現在 `docs/api/` に残っているのは Spring 時代の遺物である。

| ファイル | 現状 |
|---|---|
| `docs/api/README.md` | `springdoc` の使い方。`/swagger-ui.html` と `/v3/api-docs` を案内しているが、どちらも既に存在しない |
| `docs/api/openapi.json` | Phase 4 完了時点のスナップショット。`"tags": ["todo-controller"]` と Spring 由来の名前が残っている |

加えて [#6](https://github.com/GenkiHashioka/kotlin-todo/issues/6) が「`POST /todos` は実際は 201 を返すが仕様書上は 200」「404/400 のエラーレスポンス形も未記載」を指摘したまま残っている。

本 Phase で、**実装から生成される仕様書**を Ktor 版として再構築する。

### Ktor の OpenAPI 対応は作り直された

調査の結果、現在のプロジェクトのバージョンでは**そもそも自動生成という選択肢が存在しない**ことが分かった。

| | 本プロジェクト | 最新 |
|---|---|---|
| Ktor | **3.2.0**（2025-06-12） | **3.5.2**（2026-07-31） |
| `ktor-server-routing-openapi` | — | **3.4.0 以降にのみ存在** |

自動生成は **Ktor 3.3.0 で新登場**した機能である。コンパイラプラグイン（＝ビルド時にソースを解析して追加のコードを生成する仕組み。`@Serializable` が JSON 変換コードを自動生成するのと同じ種類のもの）が `routing { }` の中身を読み、パス・パラメータ・ボディの型を拾い上げる。

したがって本 Phase は **Ktor 本体のアップグレードを含む**。

## 2. スコープ

### この PR で作るもの

- **Ktor を 3.2.0 → 3.5.2 にアップグレード**（独立したコミット）
- `io.ktor.plugin` Gradle プラグインの導入と `ktor { openApi { enabled = true } }` の有効化
- `ktor-server-routing-openapi` / `ktor-server-swagger` 依存の追加
- `routes/TodoRoutes.kt` への仕様記述の追加
  - `route("/todos")` に `describe { }` で共通エラー（500）
  - `/{id}` を入れ子ルートに再構成し、そこに共通エラー（400 / 404）
  - 各エンドポイントに KDoc で説明文
- 仕様書の配信経路の追加
  - Swagger UI を `/swagger` に
  - 仕様 JSON を `/openapi.json` に
- 生成された仕様を検証するテスト（DB 不要、1 ファイル）
- `docs/api/` の再構築（`openapi.json` の削除、`README.md` の全面書き換え）
- ADR 0020 の追加
- `docs/architecture.md` の更新（技術スタック表、Phase 4.10 行）
- journal の追加

### この PR で作らないもの

- **ルーティングの網羅テスト（`testApplication` で CRUD 全経路）** — Phase 4.11。理由は判断 7 を参照
- **実験的 API の opt-in 方針の統一** — [#31](https://github.com/GenkiHashioka/kotlin-todo/issues/31)。本 PR ではファイル単位の暫定対応にとどめる（判断 4）
- **接続情報の外部化** — [#29](https://github.com/GenkiHashioka/kotlin-todo/issues/29)
- **認証 / セキュリティスキームの記載** — [#23](https://github.com/GenkiHashioka/kotlin-todo/issues/23) の認証実装時
- **エラーメッセージの多言語対応 / エラーコード** — [#5](https://github.com/GenkiHashioka/kotlin-todo/issues/5)
- **`dto/` のリソース軸分割** — Phase 5、[#28](https://github.com/GenkiHashioka/kotlin-todo/issues/28)
- **一覧取得の N+1 解消** — Phase 5、[#3](https://github.com/GenkiHashioka/kotlin-todo/issues/3)

## 3. 設計方針

### 判断 1: Ktor 公式の自動生成を採用し、そのために Ktor を 3.5.2 に上げる

#### 4 案の比較

| | 生成元 | Ktor 更新 | コードとの乖離 |
|---|---|---|---|
| **A. Ktor 公式の自動生成** | **routing の実コード + KDoc** | **要（→ 3.5.2）** | **起きない** |
| B. 手書き YAML + `swaggerUI()` | 手書きファイル | 不要 | 起きる |
| C. `openAPI()` の静的ファイル方式 | 手書きファイル | 不要 | 起きる |
| D. サードパーティ製ジェネレータ | 専用 DSL | 不要 | 起きない |

**A を採用する。** Spring 時代に得ていた「実装から生成される」性質を取り戻すのが本 Phase の目的そのものであり、ここで手書きに戻すと **Spring 時代より後退する**。

**B を却下する理由**: Ktor を上げずに今日から書ける最短ルートだが、5 エンドポイント × DTO 6 種を手で YAML に書き写すことになる。DTO にフィールドを 1 つ足すたび YAML も直す運用は、ソロ開発で最初に崩れる。#6 が示すとおり、仕様と実装のズレは放置すると必ず起きる。

**C を却下する理由**: 入力が手書きファイルである点は B と同じで、乖離が解決しない。加えて既定のレンダラ `StaticHtml2Codegen` は読むだけの HTML で、Swagger UI の「Try it out」（ブラウザから実際にリクエストを送る機能）が無い。Phase 4.9 で curl を大量に打った経験からすると実用上の後退である。

**D を却下する理由**: `navikt/ktor-openapi-generator` などは `get("/todos")` を専用 DSL に書き換えることを要求する。ルーティングの書き方そのものがライブラリに縛られるうえ、公式が自動生成を持った今、メンテナンスの継続性も弱い。

#### アップグレードの影響範囲

3.3 / 3.4 / 3.5 で公表されている破壊的変更は **Jetty 12 化**と **OkHttp 5 化**の 2 つ。本プロジェクトはどちらも使っていない（Netty + サーバ側のみ）。

使用中の Ktor モジュールは 5 つ。

```
ktor-server-netty / ktor-server-content-negotiation / ktor-serialization-kotlinx-json
ktor-server-call-logging / ktor-server-status-pages
```

Kotlin のバージョン要求も満たしている。OpenAPI の Gradle 拡張は Kotlin 2.2.20 以上を要求し、本プロジェクトは 2.3.21。実際 `ktor-server-routing-openapi-3.5.2` の pom が依存する `kotlin-stdlib` は **2.3.21** で一致している。

**それでもゼロリスクではないため、アップグレードは独立したコミットに切る。** テスト 10/10 が維持されることを確認してから OpenAPI の作業に進む。ここで壊れた場合は案 B に退避できる。

### 判断 2: 共通エラーは親ルートの `describe { }`、説明文は KDoc、正常系のボディ型は自動推論に任せる

#### なぜ記述が必要なのか

自動生成が読み取れるのは `routing { }` の中のコードだけである。ところが Phase 4.9 (c) で、エラーの生成場所を **StatusPages に集約**した（[ADR 0017](../decisions/0017-error-response-and-exception-mapping.md)）。

```
TodoRoutes.kt    ← 自動生成が読む場所。404 も 500 もここに書かれていない
Application.kt   ← StatusPages。ここが実際に ErrorResponse を作っている
```

集約自体は正しい判断だが、副作用として「どのエンドポイントがどのエラーを返すか」がルーティングのコードから消えた。放置すると生成される仕様書には **200 系しか載らない**。これは #6 の再発である。

#### 記述手段は 2 つある

**手段 1: KDoc コメント**（コンパイラプラグインが読む）

```kotlin
/**
 * Todo を 1 件取得する。
 *
 * Path: id [Long] Todo の ID
 *
 * Responses:
 * - 200 [TodoResponse] 見つかった Todo
 * - 404 [ErrorResponse] 該当する Todo が無い
 */
get { ... }
```

対応キーワードは `Tag` / `Path` / `Query` / `Header` / `Cookie` / `Body` / `Response`(`Responses`) / `Description` / `Security` / `Deprecated` / `ExternalDocs`。

**手段 2: `describe { }`**（`ktor-server-routing-openapi` のランタイム API）

```kotlin
route("/todos") {
    describe {
        responses {
            response(500) {
                description = "サーバ内部エラー"
                schema = jsonSchema<ErrorResponse>()
            }
        }
    }
    // ...
}
```

#### 決め手は「親に書いたものが子に継承されるか」

`ktor-server-routing-openapi-3.5.2.jar` のバイトコードで確認した。

```
private static final io.ktor.openapi.Operation operation(io.ktor.server.routing.Route)
    → InterfaceMethod io/ktor/server/routing/Route.lineage:()Lkotlin/sequences/Sequence;
    → Method kotlin/collections/CollectionsKt.asReversed:(Ljava/util/List;)Ljava/util/List;
    → Method operationFromDescribeCalls:(...)
```

`lineage()` は「そのルートと祖先すべて」の列を返す。それを逆順に並べ、各段の `describe` の内容を合成している。つまり **親ルートに 1 回書けば配下の全エンドポイントに効く**。

一方、KDoc 方式で親ブロックに書いたときに同じことが起きるかは、**公式ドキュメントに記載が無い**（各ハンドラに書く例しか載っていない）。

#### `describe` を共通エラーに使うもう 1 つの理由: 型で守られる

```kotlin
schema = jsonSchema<ErrorResponse>()   // 実際の型を参照するコード
```

これはコンパイルされるコードである。`ErrorResponse` にフィールドを足せば仕様書も自動で追随し、クラス名を変えればビルドが落ちる。対して KDoc の `[ErrorResponse]` はコメント上の文字列で、壊れても静かに仕様書からスキーマが消えるだけである。

**ADR 0017 の統一がここで効く。** 全エラーを `ErrorResponse` 1 種類に揃えたため、書くのは `jsonSchema<ErrorResponse>()` の 1 行だけで済む。`FieldError` は入れ子なので `CollectSchemaReferences` が自動的に辿って `components.schemas` に登録する。エラーの形が経路ごとに違っていたら、ここで 3 種類書くことになっていた。

#### 却下した案

- **全部 KDoc コメントで書く**: ルーティングのコードが汚れず、実験的 API への opt-in も要らない。しかし継承が保証されないため、共通エラーを各ハンドラに繰り返し書くことになる。DTO を直したとき追随が漏れる形は、手書き YAML を却下した理由と同じ。却下
- **全部 `describe { }` で書く**: 記述手段が 1 つに揃うのは魅力だが、正常系のボディ型は `codeInferenceEnabled`（既定 `true`）が `call.receive<TodoCreateRequest>()` や `call.respond(...)` からすでに推論する。それを手書きし直すのは判断 1 で自動生成を選んだ意味を打ち消す。加えて `TodoRoutes.kt` が仕様記述で埋まり見通しが悪くなる。却下
- **エラーは記述せず正常系だけ載せる**: #6 の再発。仕様書を作る目的が半分失われる。却下

### 判断 3: `/{id}` を入れ子ルートに再構成する

判断 2 の「親に書けば継承される」を活かすには、**共通のものを括れる親が存在する必要がある**。

現状の `TodoRoutes.kt` は 3 つのハンドラがそれぞれ `"/{id}"` を宣言している。

```kotlin
route("/todos") {
    get { ... }
    get("/{id}") { ... }
    post { ... }
    put("/{id}") { ... }
    delete("/{id}") { ... }
}
```

各エンドポイントが返しうるエラーを整理すると、共通部分は一様ではない。

| | 200/201 | 400 | 404 | 500 |
|---|---|---|---|---|
| `GET /todos` | ○ | — | — | ○ |
| `POST /todos` | ○ | 検証失敗 | Category 不在 | ○ |
| `GET /todos/{id}` | ○ | id が数値でない | Todo 不在 | ○ |
| `PUT /todos/{id}` | ○ | id / 検証失敗 | Todo / Category 不在 | ○ |
| `DELETE /todos/{id}` | ○ | id が数値でない | Todo 不在 | ○ |

**全エンドポイント共通なのは 500 だけ**である。一方 `{id}` を持つ 3 つは「400（id が数値でない）」と「404（Todo 不在）」を共有している。そこで `{id}` を親ルートとして括る。

```kotlin
route("/todos") {
    describe { /* 500 */ }
    get { ... }
    post { ... }
    route("/{id}") {
        describe { /* 400: id が数値でない, 404: Todo 不在 */ }
        get { ... }
        put { ... }
        delete { ... }
    }
}
```

**URL の見た目は 1 文字も変わらない。** `route("/{id}") { get { } }` と `get("/{id}") { }` はルーティングとして等価である。

副次的な利点として、`INVALID_ID_RESPONSE` を返す 3 箇所のコピーが「同じ親に属するもの」として構造的に表現される。現状はトップレベルの `private val` を 3 箇所から参照しており、共通であることがコードの形に現れていない。

**却下した案**: 現状の平坦な構造のまま、400 と 404 を 3 つのハンドラそれぞれに KDoc で書く。ルーティングを触らずに済むが、判断 2 の継承という利点を自ら捨てることになる。却下

### 判断 4: 実験的 API の opt-in は本 PR ではファイル単位にとどめる

`describe` には実験的マーカーが付いている。バイトコードで確認した。

```
public static final io.ktor.server.routing.Route describe(...)
  RuntimeInvisibleAnnotations:
    0: io.ktor.utils.io.ExperimentalKtorApi
```

したがって `TodoRoutes.kt` に opt-in（＝「実験的だと承知の上で使う」という明示的な宣言）が必要になる。

```kotlin
@file:OptIn(ExperimentalKtorApi::class)
```

**これは [#31](https://github.com/GenkiHashioka/kotlin-todo/issues/31) とまったく同じ種類の問題である。** #31 は `MissingFieldException` が実験的 API であることへの対応で、`Application.kt` に 4 件の警告が出ている。opt-in の書き方には少なくとも 3 通り（ファイル単位の `@file:OptIn` / 宣言単位の `@OptIn` / `build.gradle.kts` でモジュール全体を許可）あり、**どれを標準にするかはプロジェクト横断の判断**である。

本 PR ではファイル単位の `@file:OptIn` を暫定的に置き、**方針の統一は #31 で行う**。#31 は Phase 4.10 の後と合意済みで、そのとき対象が 2 ファイルになることを #31 に追記する。

**却下した案**: 本 PR で opt-in 方針も決めてしまう。2 箇所目が出た今が決め時とも言えるが、本 PR は Ktor 本体のアップグレードを含んでおり既に大きい。#31 は独立した Issue として立っており、そこで扱うほうが判断の記録が追いやすい。却下

### 判断 5: Swagger UI を `/swagger`、仕様 JSON を `/openapi.json` に置く

Ktor 3.5 系の自動生成は**実行時にルートツリーから仕様を組み立てる**方式で、ビルド時に静的ファイルを吐かない。したがって配信経路も実行時に用意する。

```kotlin
swaggerUI("/swagger") {
    info = OpenApiInfo("kotlin-todo API", "0.1")
    source = OpenApiDocSource.Routing()
}

get("/openapi.json") {
    // ルートツリーから組み立てた仕様を JSON で返す
}.hide()
```

**JSON の経路を別に用意する理由は 2 つ。**

1. **テストから読むため**（判断 7）。Swagger UI は HTML を返すので、機械的に中身を検証できない
2. **Spring 時代と同じ運用ができるため**。`docs/api/README.md` は `/v3/api-docs` を案内していた。パスは変わるが「生の JSON をいつでも取れる」という性質は維持する

`.hide()` を付けるのは、**仕様書自身のエンドポイントが仕様書に載るのを防ぐため**である。`hide()` は `ktor-server-routing-openapi` が提供する（`DescribeRouteKt.hide` として実在を確認済み）。

パス名は Spring 時代の `/swagger-ui.html` ではなく `/swagger` にする。Ktor 公式ドキュメントの既定に合わせ、拡張子が付かない形にする。

### 判断 6: `docs/api/openapi.json` は削除し、スナップショットをコミットしない

Spring 時代の運用は README にこう書かれている。

> 実装が進むたびに手動で最新化する想定（自動化はしていない）

**この運用は実際に腐った。** #6 が指摘した「201 なのに 200 と書いてある」は、まさに手動更新が追いつかなかった結果である。

**同じ運用を繰り返さない。** `openapi.json` を削除し、`README.md` は「起動して `/openapi.json` から取得する」という案内に書き換える。仕様の正は常に実行中のアプリであり、リポジトリ内に第二の情報源を作らない。

**却下した案**: スナップショットをコミットし続ける。API の変更が PR の diff に現れるという利点は実在する。しかし 3.5 系は静的ファイルを生成しないため、更新には「サーバを起動して curl する」という手作業が必要で、**腐る条件が Spring 時代とまったく同じ**である。代わりに判断 7 のテストが仕様の中身を検証するので、壊れたときはビルドで落ちる。却下

### 判断 7: テストは「生成された仕様の検証」に限定する

Phase 4.9 (c) の設計メモは、テストを書かない理由を 2 つ挙げていた。

> `testApplication` による Routing のテスト — Phase 4.11 のテスト戦略再構築でまとめて扱う。加えて **#25 により現在テストが実行不能**

**後半は解消した。** #25 が解決し、テストは 10/10 通る。そこで本 PR では、次の 3 点に絞ってテストを書く。

| 検証 | 何を守るか |
|---|---|
| 5 エンドポイントが仕様書に載っている | 自動生成そのものが動いていること |
| `/todos/{id}` の 404 に `ErrorResponse` スキーマが付いている | **判断 2 の継承が実際に効いていること** |
| `components.schemas` に `ErrorResponse` と `FieldError` が両方いる | 入れ子の型が自動で辿られていること |

2 番目が本命である。継承が効くという判断は**バイトコードから読み取った推論**であり、実際に動く保証まではない。テストにしておけば、一度きりの目視確認ではなく毎回のビルドで確かめられる事実になる。

**実験的 API を採用する方針と、このテストは表裏である。** Ktor 3.6 で `describe` の形が変わったとき、テストが無ければ気づく手段は「起動してブラウザで目視する」しかない。テストがあれば `./gradlew test` が落ち、どこが壊れたかまで示す。

#### このテストは DB を必要としない

本番コードを読んで確認した。`TodoService` も各 Repository も**フィールドを持たず、コンストラクタで DB に触らない**。DB アクセスはすべて `suspend fun` 内の `newSuspendedTransaction` に閉じている。

したがってテストは `TodoService(CategoryRepository(), TodoRepository())` を素で組み立て、ルートツリーを生やし、`/openapi.json` を読むだけで済む。**`AbstractPostgresTest` を継承せず、Testcontainers も起動しない**（＝数秒で終わる）。

#### 却下した案

- **テストを書かず Phase 4.11 に回す**: 現状維持で最も楽だが、#25 を直した意味が薄れる。加えて、いま導入するのはプロジェクト内で最も壊れやすい実験的 API であり、そこを守るものがゼロのまま次に進むのはリスクの配分として逆。却下
- **本 PR でルーティング網羅テストも書く**: 一見まとめて片付くが、**本番コードのリファクタを巻き込む**。`Application.module()` は `DatabaseFactory.init()` を呼び、その中に `jdbc:postgresql://localhost:5432/kotlin_todo` が直書きされている。`testApplication` から `module()` を呼ぶと Testcontainers ではなくローカルの 5432 を見にいく。テストから使うには `module()` の分割か DataSource の外部注入が必要で、これは「テスト用の接ぎ目をどこに作るか」という Phase 4.11 の主題そのものであり、[#29](https://github.com/GenkiHashioka/kotlin-todo/issues/29) とも同じコードに触る。却下

**推奨案がこの問題を避けられるのは `module()` を経由しないため。** 検証したいのは「`TodoRoutes.kt` に書いた注釈が正しい仕様書になるか」であって、アプリ全体の起動ではない。

### ADR に切り出す判断

| ADR | 主題 | 含める判断 |
|---|---|---|
| 0020 | OpenAPI 仕様は Ktor 公式の自動生成で作り、スナップショットを持たない | 判断 1、2、6 |

1 本にとどめる。判断 3（入れ子ルート）と判断 5（配信パス）は実装の詳細で、後から読み返す価値が薄い。判断 4 は #31 で、判断 7 は Phase 4.11 でそれぞれ扱う。

## 4. 実装ステップ

**コミットは以下の区切りで分ける**（変更の理由が違うため）。

### コミット 1: Ktor のアップグレード

1. `backend/build.gradle.kts` の `ktor-bom` を `3.2.0` → `3.5.2` に変更
2. `./gradlew build` を実行し、**テスト 10/10 が維持されることを確認**
3. `./gradlew run` で起動し、既存の CRUD が動くことを curl で確認（§5 の回帰確認）

**ここで問題が出たら先に進まない。** 判断 1 のとおり、案 B への退避を検討する。

### コミット 2: OpenAPI の導入

4. `plugins` に `id("io.ktor.plugin") version "3.5.2"` を追加
5. `ktor { openApi { enabled = true } }` を追加
6. 依存に `ktor-server-routing-openapi` と `ktor-server-swagger` を追加
7. `TodoRoutes.kt` を判断 3 の形に再構成（`/{id}` を入れ子に）
8. `TodoRoutes.kt` に `@file:OptIn(ExperimentalKtorApi::class)` を追加
9. `route("/todos")` と `route("/{id}")` に `describe { }` を追加
10. 各ハンドラに KDoc で説明文を追加
11. `Application.kt` の `routing { }` に `swaggerUI("/swagger")` と `get("/openapi.json")` を追加
12. 手動確認（§5）

### コミット 3: テスト

13. `backend/src/test/kotlin/.../routes/OpenApiSpecTest.kt` を新規作成
14. 判断 7 の 3 点を検証。`AbstractPostgresTest` は継承しない
15. `./gradlew test` で 13/13 になることを確認

### コミット 4: ドキュメント

16. `docs/api/openapi.json` を削除
17. `docs/api/README.md` を全面書き換え（Ktor 版の見方、`/swagger` と `/openapi.json`）
18. ADR 0020 を追加、`docs/decisions/README.md` に 1 行追記
19. `docs/architecture.md` を更新（技術スタック表に OpenAPI 行、Phase 4.10 の行を実績に）
20. `docs/README.md` の進捗を Phase 4.10 完了に更新
21. journal を追加
22. #6 に本 PR での対応状況を追記、#31 に「対象が 2 ファイルになった」旨を追記

## 5. 確認方法

### 起動

```bash
docker compose up -d postgres
cd backend && ./gradlew run
```

### 手動確認（既存機能の回帰 / コミット 1 の直後に実施）

Ktor のアップグレードで壊れていないことを、OpenAPI に着手する前に確かめる。

```bash
# ヘルスチェック
curl -i http://localhost:8080/health

# 作成 → 201 と Location ヘッダ
curl -i -X POST http://localhost:8080/todos -H "Content-Type: application/json" \
  -d '{"title":"OpenAPI を学ぶ","description":null,"dueDate":"2026-08-31","priority":"HIGH","status":"NOT_STARTED","categoryId":null}'

# 一覧 → 200
curl -i http://localhost:8080/todos

# 検証失敗 → 400 + fieldErrors
curl -i -X POST http://localhost:8080/todos -H "Content-Type: application/json" \
  -d '{"title":"","description":null,"dueDate":null,"priority":"HIGH","status":"NOT_STARTED","categoryId":null}'

# 存在しない ID → 404 + ErrorResponse
curl -i http://localhost:8080/todos/999999

# id が数値でない → 400
curl -i http://localhost:8080/todos/abc
```

**期待**: Phase 4.9 (c) 完了時点とすべて同じ挙動。

### 手動確認（仕様書の生成）

```bash
# 仕様の生 JSON
curl -s http://localhost:8080/openapi.json | python3 -m json.tool | head -60

# 5 エンドポイントが載っているか
curl -s http://localhost:8080/openapi.json | python3 -c "import sys,json; d=json.load(sys.stdin); print(json.dumps(d['paths'], indent=2)[:800])"

# スキーマに ErrorResponse と FieldError が両方いるか
curl -s http://localhost:8080/openapi.json | python3 -c "import sys,json; print(list(json.load(sys.stdin)['components']['schemas'].keys()))"
```

**期待**:
- `paths` に `/todos` と `/todos/{id}` があり、合計 5 つの操作（get / post / get / put / delete）が載る
- `/todos/{id}` の `get` に **404 が載っており、その中身が `ErrorResponse` を参照している**（＝判断 2 の継承が効いた証拠）
- `components.schemas` に `TodoResponse` / `TodoCreateRequest` / `TodoUpdateRequest` / `CategorySummary` / `ErrorResponse` / `FieldError` が並ぶ
- **`/openapi.json` 自身が `paths` に現れない**（`.hide()` が効いた証拠）

### 手動確認（Swagger UI）

ブラウザで `http://localhost:8080/swagger` を開く。

**期待**:
- 5 エンドポイントが一覧される
- `POST /todos` を開くと**リクエストボディのスキーマ**が表示される
- 「Try it out」で実際にリクエストを送れる。送った結果が curl と同じになる
- 各エンドポイントに 400 / 404 / 500 が並び、`ErrorResponse` の形が展開できる

### 自動テスト

```bash
cd backend && ./gradlew test
```

**期待**: 13/13（既存 10 + 本 PR の 3）。新規テストは Testcontainers を起動しないため、実行時間がほとんど増えないこと。

## 6. 想定される詰まりポイント

- **懸念 A: `io.ktor.plugin` と既存の `application` プラグインが衝突する**
  現在 `build.gradle.kts` は `application` プラグインを宣言し `mainClass.set(...)` を書いている。Ktor Gradle プラグインは内部で `application` を適用するため、設定が二重になる可能性がある。
  → **対処**: プラグインを足した直後に `./gradlew run` が通るかだけを先に確認する。通らなければ `application { }` ブロック側を Ktor プラグインの流儀に寄せる。この確認をコミット 2 の最初に置く。

- **懸念 B: Ktor Gradle プラグインが Ktor のバージョンを勝手に揃えにくる**
  Ktor Gradle プラグインは BOM を自動適用する性質がある。既存の `platform("io.ktor:ktor-bom:3.5.2")` と二重になった場合、どちらが勝つかが分かりにくい。
  → **対処**: `./gradlew dependencies --configuration runtimeClasspath` で実際に解決されたバージョンを目で確認する。バージョンが 1 箇所で決まっていることを journal に記録する。

- **懸念 C: コンパイラプラグインが `fun Route.todoRoutes()` を追えない**
  ルーティングが `Application.kt` ではなくローカルの拡張関数に分かれている。公式ドキュメントは「ローカルの拡張関数もマージする」と書いているが、実物で確かめていない。
  → **対処**: コミット 2 の早い段階で、**まず何も注釈を書かずに** `/openapi.json` を叩く。5 エンドポイントが素の状態で載れば追えている。載らなければ、`todoRoutes` を `Application.kt` にインライン展開する案に切り替える（記述量は増えるが仕様書は作れる）。

- **懸念 D: `codeInferenceEnabled` の推論範囲が読めない**
  `call.respond(HttpStatusCode.BadRequest, INVALID_ID_RESPONSE)` はルーティング内にあるため、400 が自動で拾われる可能性がある。その場合、判断 3 で `describe` に書く 400 と**二重に載る**恐れがある。
  → **対処**: 懸念 C と同じ「素の状態」の出力を見て、**何が自動で拾われるかを先に把握してから** `describe` に書く内容を決める。重複したら `describe` 側から 400 を落とす。順序を逆にしない。

- **懸念 E: 親ルートの `describe` が継承されない**
  判断 2 の根拠はバイトコードの読み取りであり、実際の挙動は未確認。
  → **対処**: 判断 7 のテストがまさにこれを検証する。**テストを後回しにしない**。継承が効かなければ、各ハンドラに個別に `describe` を書く形に落とす（記述は増えるが方針は変わらない）。

- **懸念 F: Swagger UI と OpenAPI 3.1 の相性**
  Ktor の公式ドキュメントが、既定のレンダラと OpenAPI 3.1 仕様の互換性に注意書きを付けている。Spring 時代のスナップショットも `"openapi": "3.1.0"` だった。
  → **対処**: ブラウザで `/swagger` を開いた時点で表示が崩れていないかを目で見る。崩れる場合は `SwaggerConfig` の `version` で Swagger UI 側のバージョンを調整できる。

- **懸念 G: Java 25 と Ktor 3.5.2 の組み合わせ**
  本プロジェクトは toolchain に Java 25 を指定している。Ktor 3.2.0 では動いているが、3.5.2 で未検証。
  → **対処**: コミット 1 の `./gradlew build` が最初の関門になる。ここで落ちたら Ktor 側ではなく toolchain 側を疑い、まず Java 21 に落として切り分ける。

- **懸念 H: `jsonSchema<ErrorResponse>()` が `fieldErrors` のデフォルト値をどう表現するか**
  `ErrorResponse.fieldErrors` は `emptyList()` の既定値を持つ。OpenAPI では「必須でないフィールド」として出るはずだが、kotlinx の `SerialDescriptor` から生成される以上、`required` の扱いが直感と違う可能性がある。
  → **対処**: 生成された `components.schemas.ErrorResponse` の `required` 配列を目で見て、journal に実際の出力を貼る。ADR 0017 で「デフォルト値を付けることが要点」と書いた判断が、仕様書上どう見えるかの記録になる。

- **懸念 I: テストが `Application.module()` を経由しないことによる乖離**
  判断 7 のテストは `todoRoutes()` を直接生やすため、**本番の `module()` が同じ仕様書を出す保証はない**。例えば `swaggerUI` の登録位置が違えば結果も変わりうる。
  → **対処**: この乖離は設計上わざと受け入れている。§5 の手動確認（実際に `./gradlew run` して `/openapi.json` を叩く）を必ず実施し、テストと手動確認の結果が一致することを journal に記録する。Phase 4.11 で `module()` に接ぎ目を作った際、このテストを `module()` 経由に移す。
