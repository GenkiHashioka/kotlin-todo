# Phase 4.10 — OpenAPI 仕様の自動生成と Swagger UI

**ステータス**: 完了。Phase 4.11（テスト戦略再構築）に進む
**開始日**: 2026-08-24
**完了日**: 2026-08-26

Spring Boot 時代、API 仕様書は `springdoc-openapi` が実装から自動生成していた。Ktor 移行でこれが失われ、`docs/api/` には Phase 4 時点のスナップショットと、既に存在しない `/swagger-ui.html` を案内する README が残っていた。本 Phase の目的は、**「実装から生成される」という性質を Ktor 版で取り戻す**こと。

設計は [design-notes/phase-04.10-openapi-and-swagger-ui.md](../design-notes/phase-04.10-openapi-and-swagger-ui.md) に事前記録済み（判断 1〜7、懸念 A〜I）。本 journal は**実装して初めて分かったこと**と**設計メモからの乖離**を主に記録する。

判断そのものは [ADR 0020](../decisions/0020-generate-openapi-from-routing.md)（生成方針）と [ADR 0021](../decisions/0021-pin-gradle-to-ide-tooling-api.md)（Gradle の固定）に切り出した。

## 学習目標

- コンパイラプラグインがルーティングのコードから何を読み取れるか、その境界がどこにあるか
- OpenAPI の構造（`paths` / `responses` / `components.schemas` / `$ref`）と Kotlin の型の対応
- 自動生成と手書きが混ざる仕組みで、記述がどう合成されるか
- Ktor のルーティングが木構造であることの実務上の意味
- `testApplication` で DB を使わずにルーティングだけを検証する方法
- 公式ドキュメントと実装が食い違うときの確かめ方

## 成果物

### routes/OpenApiRoutes.kt（新規）

- `/openapi.json` — 仕様の生 JSON。`hide()` でこの経路自身を仕様書から除外
- `/swagger` — Swagger UI
- `API_INFO` と、仕様出力専用の `openApiJson`（`explicitNulls = false` / `prettyPrint = true`）

### routes/TodoRoutes.kt（変更）

- `@file:OptIn(ExperimentalKtorApi::class)`
- `route("/todos")` に `describe` で 500
- `route("/{id}")` を新設し、配下に `get` / `put` / `delete` を移動。400 と 404 をここに記述
- `post` / `put` に個別の `describe`（`requestBody` の必須指定、操作固有の 400・404）

### Application.kt（変更）

- 仕様の配信を `openApiRoutes()` の呼び出し 1 行に整理
- `/health` に行コメントを追加（`summary` になる）

### backend/build.gradle.kts（変更）

- `id("io.ktor.plugin") version "3.5.2"` と `ktor { openApi { enabled = true } }`
- `ktor-server-routing-openapi` / `ktor-server-swagger` / `ktor-server-test-host`
- インデントをタブから 4 スペースに統一（`.kt` 側に合わせた）

### テスト（新規 4 件）

`backend/src/test/kotlin/com/example/kotlin_todo/routes/OpenApiSpecTest.kt`。合計 14 件になった。

### 依存のバージョン変更

| | 変更 | 理由 |
|---|---|---|
| Ktor | 3.2.0 → **3.5.2** | OpenAPI 生成が 3.3.0 で追加された |
| Kotlin | 2.3.21 → **2.4.10** | コンパイラプラグインが 2.4.0 以上を要求 |
| Gradle | 9.7.0 → **9.6.0**（固定） | IDE 同梱の Tooling API に合わせた（ADR 0021） |

### ドキュメント

- ADR 0020 / 0021 を追加
- `docs/api/README.md` を全面書き換え、`docs/api/openapi.json` を削除
- `docs/architecture.md` を 0.4 に更新（技術スタック表 6 件、テスト戦略、Phase 表）
- `README.md` / `docs/README.md` の進捗とバージョン

## チェックポイント結果

最終的に生成された仕様。

```
GET     /health          summary='ヘルスチェック'
          200: HealthResponse

GET     /todos           summary='一覧取得'
          200: array of TodoResponse
          500: ErrorResponse   'サーバ内部エラー'

POST    /todos           summary='作成'
          requestBody: TodoCreateRequest  required=True
          201: TodoResponse（Location ヘッダ付き）
          400: ErrorResponse   'リクエストボディが不正（…）'
          404: ErrorResponse   'categoryId に指定した Category が存在しない'
          500: ErrorResponse   'サーバ内部エラー'

GET     /todos/{id}      summary='単体取得'
          200: TodoResponse
          400: ErrorResponse   'パスパラメータの id が数値でない'
          404: ErrorResponse   '指定された Todo が存在しない'
          500: ErrorResponse   'サーバ内部エラー'

PUT     /todos/{id}      summary='更新'
          requestBody: TodoUpdateRequest  required=True
          400: ErrorResponse   'id が数値でない、またはリクエストボディの検証に失敗した'
          404: ErrorResponse   '指定された Todo、または categoryId に指定した Category が存在しない'

DELETE  /todos/{id}      summary='削除'
          （GET と同じ）

components.schemas: CategorySummary / ErrorResponse / FieldError /
                    HealthResponse / TodoCreateRequest / TodoResponse / TodoUpdateRequest
```

- `/openapi.json` と `/swagger` 自身は載っていない（`hide()` が効いている）
- Swagger UI の「Try it out」から実際にリクエストが送れることを確認
- テスト 14 件が green。`describe` を消す・入れ子を壊すなど、**わざと壊して落ちることも確認した**

## 学んだこと

### 推論が読み取れる範囲は、想像よりずっと広かった

注釈を 1 つも書かない状態で `/openapi.json` を叩いたところ、既にこれだけ載っていた。

- パス、HTTP メソッド、パスパラメータ `id`
- リクエストボディの型（`call.receive<TodoCreateRequest>()` から）
- レスポンスの型、**配列の要素型**（`array` の `items` が `TodoResponse`）
- `201` というステータスコード、**`Location` ヘッダ**（`call.response.header(...)` から）
- ルーティング内で `call.respond` している **400**
- DTO 7 種の JSON Schema。`description: String?` が `type: ["string", "null"]` になり、**デフォルト値を持つ `fieldErrors` は `required` から外れていた**
- **行コメントから `summary`**

最後の 2 つは予想していなかった。特に `fieldErrors` が `required` に入らないのは、[ADR 0017](../decisions/0017-error-response-and-exception-mapping.md) の判断 4（デフォルト値を付けることが要点）が、**仕様書の上でも正しく表現された**ということで、設計の妥当性が別の角度から確認できた。

**先に「素の状態」を見たことが、この Phase で最も効いた判断だった。** 見ずに書き始めていたら、推論が既に載せている 400 を `describe` にも書いて二重に管理していた。

### 境界は「そのハンドラのコードを読んで分かるかどうか」

自動と手書きの分かれ目には、はっきりした規則がある。

| 自動 | 手書き |
|---|---|
| `routes/` のコードに現れるもの | `Application.kt` の StatusPages が作るもの（404 / 500 / 検証失敗の 400） |
| 型から決まるもの | コードのどこにも書かれていないもの（説明文、ボディが必須かどうか） |

**404 と 500 が載らないのは、ライブラリの弱さではない。** エラーの生成を StatusPages に集約すると決めた ADR 0017 の帰結である。全自動にするにはエラー整形を 5 か所のハンドラに戻すことになり、それは ADR 0017 で却下した設計そのものだった。

**仕様書のために設計を悪くするのは本末転倒**という判断で、半自動を受け入れた。

### `describe` は「足す」のではなく「置き換える」

これが一番高くついた発見だった。`requestBody` に「必須である」ことだけを足そうとして、こう書いた。

```kotlin
requestBody { required = true }
```

結果、推論が入れていた型が消えた。

```
required = True
content  = None        ← TodoCreateRequest が消えた
```

**部分的な上書きはできない。書く要素は、必要な情報をすべて書く。**

不便に見えるが、曖昧さが無いという利点がある。部分マージを許すと「どこまでが推論で、どこからが記述か」がコードから読めなくなる。**「書いたものがそのまま出る」なら、コードを見れば結果が分かる。**

### 記述の強さは「推論 < 祖先の describe < 自分の describe」

実測で確定した順序。

- **500** を `route("/todos")` に書いたら、5 操作すべてに届いた（誰も持っていない項目 → 祖先が採用される）
- **400** を `route("/{id}")` に書いたら、GET / DELETE にも届いた（**推論が持っていた項目を、祖先が上書きした**）
- **404** を `put` に書いたら、親の短い説明を上書きできた（自分 > 祖先）

この規則のおかげで、**「既定は親に、例外だけ子に」**という書き方ができる。同じ文章を 3 か所に複製せずに済む。

なお切り分けの途中で「祖先は推論に負ける」と誤った結論を出しかけた。**親に 400 を書いたつもりで書けていなかった**のを、「書いたのに効かない」と読み違えたためである。**実測と称して、実際には何も測っていなかった。**

### Ktor のルーティングは木で、同じパスの節は共有される

`get("/{id}")` は「`{id}` という節を作り、その下に GET を置く」の省略記法で、木の上では 2 段になる。

```
route("/todos")
 ├── get
 ├── post
 └── "{id}"          ← get("/{id}") が暗黙に作る節
      ├── get
      ├── put
      └── delete
```

**この `{id}` の節はもともと存在している。** ただしコードの上に名前として現れないので `describe` を付けられない。`route("/{id}") { ... }` と明示的に書くのは、**同じ節を掴める形にする**という操作であって、新しい節を作っているわけではない。

実際、途中で `route("/{id}") { describe { ... } }` だけを書き、ハンドラは外に `get("/{id}")` のまま残した状態があった。**それでも記述は効いていた。** 同じ節を指しているからである。

ただし**コードの見た目と木の構造が食い違う**ので、最終的にハンドラを箱の中に移した。動作は変わらないが、「同じ節だから効く」という知識が無いと間違いに見える。

### 行コメントが公開ドキュメントになった

```kotlin
// 一覧取得
get {
```

これが `summary: "一覧取得"` として仕様書に出る。**これまで自分用のメモだったものが、API を使う人が読む説明を兼ねるようになった。**

書き方を変える必要はないが、性質が変わったことは意識しておく価値がある。`// 一覧取得（N+1 があるので後で直す）` のようなメモを書けば、それがそのまま公開される。

### 設定の適用範囲を意識して切り替える

生成された JSON は 33,861 バイトのうち **`null` が 1,688 個**あり、読める状態ではなかった。`kotlinx.serialization` が既定で値が `null` のフィールドも書き出すためである。

`ContentNegotiation` の `json()` に `explicitNulls = false` を入れれば消えるが、**それはやってはいけない**。`TodoResponse.description` が `null` のときにキーごと消え、**API の約束事が変わる**からである。受け取る側にとって「キーが無い」と「値が null」は違う。

そこで `/openapi.json` だけ専用の `Json` で変換し、`call.respond(doc)` を `call.respondText(openApiJson.encodeToString(doc), ContentType.Application.Json)` に変えた。

- `call.respond(doc)` — **変換を `ContentNegotiation` に任せる**書き方。全体設定が効く
- `call.respondText(文字列, 型)` — **自分で変換した文字列をそのまま返す**書き方。全体設定の影響を受けない

**「設定を変えて直す」前に、その設定がどこまで効くかを確認する。** 今回は 1 経路のためにアプリ全体の JSON 表現を変えるところだった。

### 公式ドキュメントより実装を信じる必要があった

2 件、ドキュメントの記載どおりでは動かなかった。

| | ドキュメント | 実装 |
|---|---|---|
| Kotlin の必要バージョン | 2.2.20 以上 | **2.4.0 以上**（`KotlinVersion.V2_4_0` で判定） |
| KDoc の書式 | `Responses:` 見出し + 箇条書き | **`Response:` の 1 行形式**（`responses` は無効） |

前者は**警告 1 行を出して黙って無効化される**という形で現れた。ビルドは成功するので、気づかなければ「設定したのに型が載らない」と延々悩むことになる。

後者はコンパイラプラグインの実装（`OpenApiCommentParserKt`）を読んで判明した。行の解析は `^([\w -_]+):\s*(.*)$` という正規表現で行われ、受け付けるキーワードの一覧も定数として持っている。**`response`（単数）はあるが `responses`（複数）は無い。**

正しい書式に直しても型は反映されず、結局 KDoc から使えるのは **1 行目（`summary`）だけ**だった。この経緯は [#37](https://github.com/GenkiHashioka/kotlin-todo/issues/37) に記録した。

**実験的機能ではドキュメントが実装に追いついていない**という前提で動くほうが早い。今回は 2 回とも、jar を展開して `javap` で中身を読むことで確定させた。

### 型が守るのは「存在しない名前」まで

`PUT` の `requestBody` に、`TodoUpdateRequest` ではなく **`TodoCreateRequest`** と書いた。

```kotlin
schema = jsonSchema<TodoCreateRequest>()   // PUT なのに Create
```

`jsonSchema<T>()` はコンパイルされるコードなので、**存在しない型名を書けばビルドが落ちる**。しかし `TodoCreateRequest` は実在する型なので、何の問題もなく通る。

しかも 2 つの DTO は**中身が完全に同じ**で、生成される JSON Schema も同一である。**Swagger UI を開いても違いが見えない。**

気づけたのは `components.schemas` の数が 7 から 6 に減ったからだった。`PUT` の `requestBody` が唯一の参照元だったため、`TodoUpdateRequest` がどこからも参照されなくなって一覧から落ちた。

**「型で守られているから安心」は、限界を知って初めて言える。** 手書き部分が増えるほど、この種の間違いの余地が増える。テストを書く動機がここで具体的になった。

### テストは「落ちるところ」を見て初めて意味を持つ

4 件のテストを書いた後、`route("/{id}")` の `describe` をコメントアウトして落ちることを確認した。

通ることだけを確認したテストは、**何も検証していない可能性を排除できない**。今回は特に、JSON の深い階層を辿る検証なので、パスを 1 つ間違えれば `null` 同士を比べて通ってしまう。

`assertEquals` の 3 つ目の引数（メッセージ）も、3 メソッドをループで回す都合で必要になった。

```
expected:<#/components/schemas/ErrorResponse> but was:<null>   ← どれが落ちた？
delete /todos/{id} の 404 ==> expected:<...> but was:<null>     ← 一目で分かる
```

## design-note からの乖離

### 乖離 1: Kotlin の必要バージョンが違った

設計メモの判断 1 には「OpenAPI の Gradle 拡張は Kotlin 2.2.20 以上を要求し、本プロジェクトは 2.3.21」と書いた。**公式ドキュメントの記載をそのまま信じた結果で、誤りだった。**

実際は 2.4.0 以上で、2.3.21 のままではコンパイラプラグインが適用されない。**設計メモはそのまま残す**（当時こう考えていた記録として）。

### 乖離 2: KDoc で説明文を書く前提が崩れた

判断 2 は「共通エラーは `describe`、各エンドポイントの説明文は普通の KDoc」という分担だった。**KDoc 側が成立しなかった。**

結果として説明文の方針を作り直し、「400 だけ書き、200 系は空のまま」という形にした（ADR 0020 判断 5、[#37](https://github.com/GenkiHashioka/kotlin-todo/issues/37)）。

### 乖離 3: 書く量は、減った部分と増えた部分がある

**減った**: 判断 3 は `/{id}` の親に「400 と 404」を書く想定だったが、**400 は推論が既に載せていた**ため、最初は 404 だけで足りると考えた。

**増えた**: `describe` が全置換であるため、説明文を足すだけのつもりの箇所でも型を書き直すことになった。`PUT` の `requestBody` はその代表で、`required` と `schema` の両方を書いている。

### 乖離 4: 懸念のうち 3 つは杞憂、3 つは的中した

| 懸念 | 結果 |
|---|---|
| A: `io.ktor.plugin` と `application` プラグインの衝突 | **杞憂**。そのまま動いた |
| B: Gradle プラグインが BOM を二重適用する | **杞憂**。全モジュールが 3.5.2 で揃った |
| C: `fun Route.todoRoutes()` を追えない | **杞憂**。ローカルの拡張関数も問題なく解析された |
| D: `codeInferenceEnabled` の推論範囲が読めない | **的中**。`INVALID_ID_RESPONSE` の 400 が自動で載っていた |
| E: 親ルートの `describe` が継承されない | **的中しなかった**（継承は効いた）。ただし確認は必須だった |
| G: Java 25 と Ktor 3.5.2 の組み合わせ | **杞憂**。JDK 3 種すべてでビルド成功 |
| H: `fieldErrors` のデフォルト値がどう表現されるか | **想定どおり**。`required` から外れた |
| I: テストが `module()` を経由しない乖離 | **受け入れたまま**。Phase 4.11 で解消する |

**懸念 D を事前に書いていたおかげで、「先に素の状態を見る」という手順を踏めた。** 設計メモの効果が最もはっきり出た箇所である。

### 乖離 5: 設計メモに無かった作業が 2 つ増えた

- **`explicitNulls` の切り替え**。生成結果を見るまで問題に気づかなかった
- **`openApiRoutes()` の切り出し**。テストから本番と同じ経路を組み立てるために必要になった

どちらも「実装して初めて見えた」もので、設計メモの限界を示している。

## 環境の事故と、その切り分け

**この Phase の作業時間の大半は、OpenAPI ではなく環境の不具合に費やされた。** 教訓として記録する。

### 症状

IntelliJ の Gradle 同期が成立しなくなった。

- 同期は `resolution task executed`（成功）と記録される
- しかし取り込まれたモジュールは **0 件**
- エディタは `io` / `kotlinx` / `java.io.Serializable` まで含めて**全シンボルが未解決**
- **UI にはエラーが一切出ない**。赤い項目も通知も無く、Build ツールウィンドウは「backend: 失敗」と無関係な JVM 警告だけ

一方、CLI の `./gradlew build` は JDK 3 種すべてで成功していた。

### 原因は 2 つ重なっていた

**原因 1: WSL の `/tmp` が満杯だった。** systemd が `/tmp` を 7.9GB の tmpfs（RAM 上）としてマウントしており、IntelliJ が同期のたびに約 192MB の作業ディレクトリを作って**セッション中は消さない**ため、約 40 回で埋まった。

```
NotEnoughSpace(where=/tmp/QA98Zp/gradle-api-9.6.0.jar.part, message=No space left)
```

160 個のディレクトリが溜まり、**15GB の物理メモリのうち 7.7GB を占有**していた。`/tmp` を実ディスクに移して解消した。

**原因 2: Gradle 9.7 系と IDE の Tooling API が非互換だった。** IntelliJ 2026.2.1 が同梱するのは `gradle-api-9.6.0.jar` で、9.7 系のデーモンと組み合わせると同期が壊れる（[ADR 0021](../decisions/0021-pin-gradle-to-ide-tooling-api.md)）。

### 教訓

**原因を 1 つだと決めつけたことが、切り分けを長引かせた。** `/tmp` を直しても症状が変わらなかった時点で「別の原因もある」と考えるべきだったが、`/tmp` の対処が誤っていたと考えて、キャッシュの削除・設定のリセット・リモート開発への切り替えを試し、いずれも外した。

**上のエラーメッセージには最初から答えが書かれていた。** `gradle-api-9.6.0.jar` — プロジェクトは 9.7.0 なのに、IDE が 9.6.0 の jar を送ろうとしている。ファイル名を読み飛ばしていた。

**「片方を直しても症状が変わらない」は、対処が間違っていた証拠にはならない。** 原因が複数ある可能性を常に残す。

## 実運用に関わる既知の課題（今回は対応を見送り）

### 正常系の `description` が空（[#37](https://github.com/GenkiHashioka/kotlin-todo/issues/37)）

`200` / `201` の説明文が空文字のまま。OpenAPI では必須項目とされているため、形式は満たすが中身が無い。`describe` が全置換であるため、説明文を足すには型や `Location` ヘッダを手で複製することになる。

実験的機能である以上、Ktor 側の更新で状況が変わりうる。再検討の条件を含めて #37 に記録した。

### 実験的 API への opt-in 方針が未統一（[#31](https://github.com/GenkiHashioka/kotlin-todo/issues/31)）

`describe` は `@ExperimentalKtorApi` を持つため、`TodoRoutes.kt` と `OpenApiRoutes.kt` に `@file:OptIn` を置いた。`Application.kt` の `MissingFieldException` と合わせて**対象が 3 ファイル**になった。書き方（ファイル単位 / 宣言単位 / モジュール全体）の統一は #31 で決める。

### セキュリティスキームの記載が無い（[#23](https://github.com/GenkiHashioka/kotlin-todo/issues/23)）

認証が未実装のため。実装時に `describe` の `security { }` で追加する。

### テストが `Application.module()` を経由しない

`OpenApiSpecTest` は `todoRoutes()` と `openApiRoutes()` を直接組み立てるため、**本番の `module()` が同じ仕様書を出す保証はない**。`module()` が `DatabaseFactory.init()` を内部で呼び、接続先を決め打ちしているためテストから使えない。

これは「テスト用の接ぎ目をどこに作るか」という Phase 4.11 の主題であり、[#29](https://github.com/GenkiHashioka/kotlin-todo/issues/29)（接続情報の外部化）とも同じコードに触れる。Phase 4.11 で解消する。

### Gradle を上げられない（[ADR 0021](../decisions/0021-pin-gradle-to-ide-tooling-api.md)）

9.6.0 に固定した。IDE が新しい Tooling API を同梱するまで解除できない。**エラーを出さずに壊れる**種類の不具合なので、Gradle を上げる際は IDE 同梱の `gradle-api-*.jar` を先に確認する。
