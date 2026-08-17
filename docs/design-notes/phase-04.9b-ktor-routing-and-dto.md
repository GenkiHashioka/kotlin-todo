# 詳細設計メモ - Phase 4.9 (b): Ktor Routing と DTO

**バージョン**: 0.1
**最終更新**: 2026-08-16
**対応 Phase**: Phase 4.9 (b)

## 1. 背景

要件書 [4.1 Todo 管理（コア機能）](../requirements.md) の CRUD API を、Ktor + Exposed のスタック上で復活させる。

Phase 4（Spring Boot 版）では `TodoController` + `@RestControllerAdvice` で提供していた API が、Phase 4.7 の Spring 削除以降ずっと落ちたままになっている。現在稼働しているエンドポイントは `/health` のみ。

Phase 4.9 は 3 分割しており、本 PR はその 2 番目にあたる。

| サブフェーズ | 内容 | 状態 |
|---|---|---|
| (a) | Service 層 + カスタム例外 | 完了（PR #19） |
| **(b)** | **DTO + Routing + 手動 DI + 開発用固定ユーザー** | **本 PR** |
| (c) | Konform（バリデーション）+ StatusPages の拡充 | 次 |

(b) 完了時点で「**CRUD が正しく動く API**」になる。「**不正な入力に正しく応答する API**」になるのは (c) 完了時点。

## 2. スコープ

### この PR で作るもの

- `dto/` パッケージ一式（リクエスト 2 種、レスポンス 2 種、`java.time` 用シリアライザ 2 種）
- `routes/TodoRoutes.kt`（Todo の CRUD 5 エンドポイント）
- `dev/DevDataInitializer.kt`（開発用固定ユーザーの用意。認証実装時に削除する暫定コード / #23）
- `Application.kt` の更新（手動 DI の組み立て、StatusPages への NotFound ハンドラ 2 件登録、`todoRoutes` の接続）

### この PR で作らないもの

| 対象 | 送り先 | 理由 |
|---|---|---|
| Konform によるバリデーション | Phase 4.9 (c) | 入力検証は独立した関心事。まず CRUD を通す |
| `SerializationException` → 400 のハンドリング | Phase 4.9 (c) | 同上。現状は 500 が返る |
| 予期しない `Throwable` → 500 の整形 | Phase 4.9 (c) | 同上 |
| `ErrorResponse.fieldErrors` | Phase 4.9 (c) | バリデーション実装と同時でないと形が決まらない |
| Category の CRUD エンドポイント | Phase 5 以降 | 要件書 4.2 の範囲。今回は Todo に集中 |
| Routing 層のテスト | Phase 4.11 | `testApplication` を使ったテスト戦略の再構築が別 Phase として存在する |
| N+1 問題の解消 | Phase 5（#3） | `TodoResponse.category` の取得は素朴な実装で通す |
| OpenAPI / Swagger UI | Phase 4.10 | springdoc 廃止済み、Ktor 版の選定から |

## 3. 設計方針

### 判断 1: DTO を 4 クラスに分ける（Create と Update を統合しない）

- 選択肢 1-1: `TodoCreateRequest` / `TodoUpdateRequest` を分ける
- 選択肢 1-2: 中身が同一なので `TodoRequest` 1 つに統合する

**採用: 1-1**。現時点では両者のフィールドは完全に同一だが、「作成時は `status` を省略可（既定 `NOT_STARTED`）、更新時は必須」のようなルールを入れたくなった時点で統合版は破綻する。分けておけば片方だけ変更できる。重複コストより将来の分岐可能性を優先した。

作成するクラス:

| クラス | 用途 | フィールド |
|---|---|---|
| `TodoCreateRequest` | `POST /todos` の入力 | title, description, dueDate, priority, status, categoryId |
| `TodoUpdateRequest` | `PUT /todos/{id}` の入力 | 同上（`id` は URL パスから取るので含めない） |
| `TodoResponse` | 全エンドポイントの出力 | id, title, description, dueDate, priority, status, category, createdAt, updatedAt |
| `CategorySummary` | `TodoResponse.category` に入れ子 | id, name |
| `ErrorResponse` | エラー時の出力 | status, message |

ドメインクラス `Todo` をそのまま JSON にしない理由:

- `Todo.ownerId` はレスポンスに出す必要がない（誰の TODO かは認証情報から決まるべきで、晒す意味がない）
- リクエスト側では `id` / `createdAt` / `updatedAt` を受け取ってはいけない（サーバーが決める値）
- `Todo.categoryId: Long?` は生の外部キー。API としては `category: CategorySummary?` を返したい

### 判断 2: enum は `.name` をそのまま JSON 文字列にする

- 選択肢 2-1: kotlinx.serialization のデフォルト（`Priority.HIGH` → `"HIGH"`）
- 選択肢 2-2: `@SerialName("high")` で小文字化する

**採用: 2-1**。同じ概念が DB カラム値 / Kotlin の enum 定数 / JSON の 3 箇所に現れるが、デフォルトのままなら全て `HIGH` で揃う。`@SerialName` を入れると JSON だけ別語彙になり、ログを追う際に読み替えが必要になる。小文字 snake_case が要求されるのは外部公開 API の規約に合わせる場合だが、本プロジェクトは自前のフロントエンドとしか通信しないためその制約はない。

**既知の欠落**: 未定義の値（例 `"URGENT"`）を送られると `SerializationException` が投げられるが、(b) 時点ではハンドラが無いため **500** が返る。本来は 400。Phase 4.9 (c) で修正する。

### 判断 3: `java.time` 型は自作の `KSerializer` で変換する

kotlinx.serialization は Kotlin Multiplatform 向けライブラリのため、JVM 固有の `java.time` 型のシリアライザを持たない。`kotlinx-serialization-core-jvm 1.8.1` の jar を確認し、日付関連クラスが 1 つも含まれていないことを実物で確認済み。`@Serializable` なクラスに `LocalDate` を素で置くとコンパイルエラーになる。

- 選択肢 3-1: DTO 側を `String` にして、Routing 層で `LocalDate.parse` / `toString` を手書きする
- 選択肢 3-2: `KSerializer` を自作し、DTO は `java.time` 型のまま書く
- 選択肢 3-3: ドメインごと `kotlinx-datetime` に移行する

**採用: 3-2**。理由は 2 つ。

1. **エラーの出口が揃う**。3-1 では日付のパース失敗が Routing 層の自前コードの中で起きるが、3-2 では JSON デシリアライズ中に起きる。Phase 4.9 (c) で「JSON 読み込み中の例外は一律 400」という 1 本のルールで捌けるのは 3-2。
2. **`KSerializer` は kotlinx.serialization を使う上での基礎知識**であり、今後も再登場する。ここで習得しておく価値がある。

3-3 を採らない理由: Exposed 側も `exposed-java-time` から `exposed-kotlin-datetime` に差し替えることになり、DB 層まで影響が波及する。Routing を通すという本 PR の目的に対して変更範囲が大きすぎる。

適用方法は、DTO ファイル先頭の `@file:UseSerializers(...)` で当該ファイル内の全フィールドに一括適用する（フィールドごとの注釈は書かない）。

### 判断 4: 開発用固定ユーザーは起動時に「無ければ作る」

`Todo.ownerId` は NOT NULL であり `TodoService.create()` も必須で要求するが、認証は未実装。リクエストボディで `ownerId` を受け取るのは論外（他人の TODO を作成できてしまう）。

- 選択肢 4-1: Flyway の migration に `INSERT` を書く
- 選択肢 4-2: 起動時に Kotlin コードで find-or-create する
- 選択肢 4-3: 手動で `psql` から投入する

**採用: 4-2**。

4-1 を却下する理由: migration はどの環境でも同じように流れるため、開発専用データを混ぜると将来の本番環境にも同じユーザーが作られる。環境依存のデータを migration に置いてはいけない。

4-3 を却下する理由: README の起動手順に「まず psql でユーザーを作る」という一文が増え、`docker compose up` → `./gradlew run` で動く状態が壊れる。

実装は `findByEmail(...) ?: create(...)` の形にして**冪等**にする。何度再起動しても重複しない。

### 判断 5: 暫定コードは `dev/` パッケージに隔離する（削除単位を作る）

Phase 4（Spring 版）では `config/DevDataInitializer.kt` に置いていたが、今回は **`dev/` パッケージ**に移す。

理由は、**パッケージを「削除の単位」にするため**。`db/` に混ぜると認証実装時に「`DatabaseFactory` は残す、`DevDataInitializer` は消す」という選別が必要になるが、`dev/` ならディレクトリごと削除すれば済む。技術的な役割（DB を触る）ではなく**寿命（いずれ消える）**を分類軸に採った。

消し忘れ防止は 3 層で担保する:

1. **GitHub Issue #23** — 削除対象をチェックリスト化。`gh issue list` で常に目に入る
2. **`// TODO(#23)` コメント** — Issue 番号付き。`rg 'TODO\(#23\)'` と IntelliJ の TODO ツールウィンドウで検出可能
3. **`dev/` パッケージの存在自体** — 残っていること自体が未削除のシグナル

`@Deprecated` は採用しない。「もう使うな」という意味の注釈であり、今まさに正規の手段として使うコードに付けると使用箇所すべてに警告が出る。警告に慣れると本物の警告も見落とすようになるため逆効果。

固定ユーザーの ID は `Application.module()` で解決し、`Route.todoRoutes(todoService, devUserId)` の引数として渡す。渡す型は `User` ではなく `Long`（Routing 層が使うのは ID だけで、email や passwordHash は不要）。

`ensureDevUser` は `suspend` 関数だが `Application.module()` は通常関数のため、`runBlocking` で橋渡しする。リクエスト処理中の `runBlocking` はスレッドを塞いで並行性を損なうため厳禁だが、起動時に 1 回だけ走る処理であれば問題ない。むしろ「ユーザーの準備完了までサーバーを起動させない」という意味で、待つのが正しい。

### 判断 6: Routing は `Route` の拡張関数として切り出す

- 選択肢 6-1: `Application.kt` の `routing { }` に直接書く
- 選択肢 6-2: `routes/TodoRoutes.kt` に `fun Route.todoRoutes(...)` として切り出す

**採用: 6-2**。`Application.kt` の役割を「サーバー設定と組み立て」に限定し、API の中身は別ファイルに置く。Category や User のルートが増えても `Application.kt` は膨らまない。

拡張関数にする理由は、関数内で `this` が `Route` になり `get` / `post` をそのまま呼べるため。通常関数 `fun todoRoutes(route: Route, ...)` にすると中身が全て `route.get(...)` になり読みにくい。呼び出し側の `routing { }` も `this: Route` なので `todoRoutes(...)` とそのまま書ける。Ktor 公式ドキュメントの標準的な書き方でもある。

パスは `route("/todos") { }` でネストさせ、`/todos` という文字列を 1 箇所にまとめる。将来 `/api/v1/todos` に変更する際の修正が 1 行で済む。

### 判断 7: `TodoResponse` に Category 情報を入れ子で含める

- 選択肢 7-1: `categoryId: Long?` をそのまま返す
- 選択肢 7-2: `category: CategorySummary?`（id + name）を入れ子で返す

**採用: 7-2**。7-1 だとフロントエンドが Category 名を表示するために別途 `GET /categories` を叩く必要があり、往復が増える。

**既知の性能問題**: Todo 一覧の各要素で Category を引くと、N 件の Todo に対して 1 + N 回の SELECT が発行される（N+1 問題）。Exposed には JPA の永続化コンテキストに相当するキャッシュがないため、同一 Category を参照していても都度クエリが飛ぶ。本 PR では素朴な実装で通し、`Todos.leftJoin(Categories)` による解消は Phase 5 で行う（Issue #3）。

### 判断 8: DTO ⇄ ドメインの変換は Routing 層で行う

Service 層は HTTP を知らない状態を保つ。`TodoService` の引数と戻り値はドメイン型（`Todo`, `Priority`, `TodoStatus`）のままとし、DTO は Routing 層で組み立てる / 分解する。

変換は拡張関数として DTO 側のファイルに置く（Phase 4 でも同じ方式を採っていた）。

```
Todo.toResponse(category: Category?): TodoResponse
```

### 判断 9: NotFound 系のエラー処理は (b) に前倒しする

`architecture.md` の「例外のカテゴリと HTTP 対応」節は Phase 4.9 (c) 実装予定と記載されているが、(b) の検証条件には「存在しない ID で 404」が含まれており、両立しない。現状の `install(StatusPages) { }` はハンドラが空のため、`TodoNotFoundException` は素の 500 になる。

- 選択肢 9-1: (b) は 500 のまま、(c) で一括対応する
- 選択肢 9-2: (b) で NotFound 系ハンドラ 2 件だけ先に入れる
- 選択肢 9-3: Routing 層で `try/catch` する

**採用: 9-2**。PR を分ける基準は「完了時点でシステムが説明可能な状態か」であり、9-1 だと (b) の完了状態が「正常系だけ動き、異常系は全て 500」という中途半端なものになる。「存在しない ID で 404」は CRUD API の**正しさの一部**であって追加機能ではない。

9-3 を却下する理由: 同じ `catch` が 5 エンドポイントに重複し、(c) で StatusPages を入れた時点で全て削除することになる。

これに伴い `ErrorResponse` も (b) で作る。形は `architecture.md` の記載に従い `status: Int` + `message: String` の 2 フィールド。`fieldErrors` は (c) で追加する。

### 判断 10: 数値でないパスパラメータは Routing 層で 400 にする

`GET /todos/abc` のようなケースは例外ではなくパラメータの読み取り失敗なので、StatusPages ではなく Routing 層の分岐で処理する。

```kotlin
val id = call.parameters["id"]?.toLongOrNull()
    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse(400, "Invalid id"))
```

`call.parameters["id"]` は `String?` を返すため、`toLongOrNull()` で数値化を試み、失敗したら 400 を返す。

## 4. 実装ステップ

1. `dto/serializer/LocalDateSerializer.kt` を新規作成（`KSerializer<LocalDate>`、文字列として入出力）
2. `dto/serializer/LocalDateTimeSerializer.kt` を新規作成（同様に `KSerializer<LocalDateTime>`）
3. `dto/CategorySummary.kt` を新規作成（`@Serializable data class`、id + name）
4. `dto/TodoCreateRequest.kt` / `dto/TodoUpdateRequest.kt` を新規作成（`@file:UseSerializers` を先頭に付与）
5. `dto/TodoResponse.kt` を新規作成 + `Todo.toResponse(category: Category?)` 拡張関数を同ファイルに定義
6. `dto/ErrorResponse.kt` を新規作成（status + message）
7. `dev/DevDataInitializer.kt` を新規作成（`object`、`ensureDevUser` を find-or-create で実装、`// TODO(#23)` を付与）
8. `routes/TodoRoutes.kt` を新規作成（`fun Route.todoRoutes(todoService, devUserId)`、CRUD 5 本）
9. `Application.kt` を修正
   - 手動 DI の組み立て（Repository 3 種 → `TodoService`）
   - `runBlocking { DevDataInitializer.ensureDevUser(userRepository).id }`（`// TODO(#23)` を付与）
   - `install(StatusPages)` に NotFound 系ハンドラ 2 件を登録
   - `routing { }` に `todoRoutes(todoService, devUserId)` を追加
10. `./gradlew build` でコンパイル確認
11. `./gradlew test` で既存 10 テストが緑のままであることを確認（Routing のテストは Phase 4.11）
12. 手動 curl で 5 エンドポイント + エラーケースを確認
13. `architecture.md` のモジュール構造（`dto/` / `routes/` の「追加予定」表記、および未記載の `dev/`）を**実装後の実態に合わせて更新**する

なお `architecture.md` の「例外のカテゴリと HTTP 対応」節は、判断 9 の前倒しを反映するため**本 design-note と同じコミットで先に修正済み**。モジュール構造のほうは実装結果に依存する（懸念 E の対処次第でファイル構成が変わり得る）ため、ステップ 13 で実装後に更新する。

## 5. 確認方法

### 起動

```bash
docker compose up -d postgres
cd backend && ./gradlew run
```

起動ログに Flyway の migration 実行と、開発用ユーザーの用意が出ること。2 回目以降の起動でユーザーが重複しないこと（冪等性の確認）。

### 手動確認（正常系）

```bash
# 作成 → 201 + Location ヘッダ
curl -i -X POST http://localhost:8080/todos \
  -H "Content-Type: application/json" \
  -d '{"title":"Ktor を学ぶ","description":"Routing まで","dueDate":"2026-08-31","priority":"HIGH","status":"NOT_STARTED","categoryId":null}'

# 一覧 → 200
curl -i http://localhost:8080/todos

# 単体取得 → 200
curl -i http://localhost:8080/todos/1

# 更新 → 200
curl -i -X PUT http://localhost:8080/todos/1 \
  -H "Content-Type: application/json" \
  -d '{"title":"Ktor を学ぶ（更新）","description":null,"dueDate":null,"priority":"MEDIUM","status":"IN_PROGRESS","categoryId":null}'

# 削除 → 200 + 削除した内容が body に入る（ADR 0006）
curl -i -X DELETE http://localhost:8080/todos/1
```

### 手動確認（異常系）

| リクエスト | 期待 | 備考 |
|---|---|---|
| `GET /todos/9999` | 404 + `{"status":404,"message":"Todo not found: id=9999"}` | 判断 9 |
| `PUT /todos/9999` | 404 | 同上 |
| `DELETE /todos/9999` | 404 | 同上 |
| `POST /todos` で `categoryId: 9999` | 404（Category not found） | 同上 |
| `GET /todos/abc` | 400 + `Invalid id` | 判断 10 |
| `POST /todos` で `"priority":"URGENT"` | **500**（本来 400） | (c) で修正。この時点では期待通りの挙動 |
| `POST /todos` で `title` 欠落 | **500**（本来 400） | 同上 |

日付が `"2026-08-31"` の形式で入出力されていることも併せて確認する（判断 3 の動作確認）。

### 自動テスト

本 PR では新規テストを追加しない。Routing 層のテスト（`testApplication` を使った統合テスト）は Phase 4.11 のテスト戦略再構築で扱う。

既存の Repository テスト 5 件 + Service テスト 5 件が緑のままであることを確認する。

```bash
cd backend && ./gradlew test
```

## 6. 想定される詰まりポイント

- **懸念 A: `@file:UseSerializers` の位置**
  ファイル最上部、`package` 宣言よりも**さらに上**に書く必要がある。`package` の下に置くとコンパイルエラーになる。
  → 対処: 1 ファイル書いて通ることを確認してから残りに展開する。

- **懸念 B: nullable な `LocalDate?` にカスタムシリアライザが効くか**
  `dueDate: LocalDate?` のように nullable な場合でも `@file:UseSerializers` は適用される（null のラップは kotlinx.serialization 側が処理する）。
  → 対処: 効かない場合はフィールドに `@Serializable(with = LocalDateSerializer::class)` を個別付与する。

- **懸念 C: Ktor 3.x のハンドラ内で `call` が解決できない**
  Ktor 2.x では `PipelineContext`、3.x では `RoutingContext` がハンドラのレシーバになっている。3.x でも `call` はそのまま参照できるが、古い記事のコードをコピーすると import が合わない可能性がある。
  → 対処: import は `io.ktor.server.routing.*` / `io.ktor.server.request.receive` / `io.ktor.server.response.respond` を基準にする。

- **懸念 D: `return@get` の書き方**
  判断 10 の 400 レスポンスで早期リターンする際、ラムダの種類ごとにラベルが変わる（`return@get` / `return@post` / `return@put` / `return@delete`）。コピペで書くと取り違えやすい。
  → 対処: エンドポイントごとに書き換える。あるいは早期リターンを使わず `if / else` で分岐する。

- **懸念 E: `TodoResponse` を作るのに Category を引く必要がある**
  `Todo` は `categoryId: Long?` しか持たないため、`CategorySummary` を組み立てるには `CategoryRepository.findById` を別途呼ぶ必要がある。`TodoService` の現在のコンストラクタは `categoryRepository` を持っているが、Routing 層からは `TodoService` 経由でしか Service に触れない。
  → 対処: `Application.kt` で組み立てた `categoryRepository` を `todoRoutes` にも渡す。あるいは `TodoService` に `findCategoryById` のような委譲メソッドを足す。前者のほうが Service の責務を膨らませずに済むため、まず前者で試す。

- **懸念 F: 一覧取得時の N+1 が体感できるか**
  判断 7 の通り N+1 は承知の上で実装する。`logback.xml` の設定次第では発行 SQL がログに出るため、実際に 1 + N 回出ていることを目視できる可能性がある。
  → 対処: 確認できたら journal に記録し、Issue #3 の裏付けとして残す。

- **懸念 G: `runBlocking` の import**
  `kotlinx.coroutines.runBlocking`。Ktor 側にも似た名前のものはないが、IDE の補完で別パッケージを拾わないよう注意する。

- **懸念 H: 既存テストが壊れる可能性**
  `Application.module()` を変更するため、もし `module()` を参照しているテストがあれば影響する。現時点の 10 テストは Repository / Service 層のみで `module()` を使っていないため影響しない見込み。
  → 対処: `./gradlew test` を早めに 1 回流して確認する。
