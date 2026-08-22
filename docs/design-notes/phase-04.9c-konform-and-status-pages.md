# 詳細設計メモ - Phase 4.9 (c): Konform バリデーションと StatusPages 拡張

**バージョン**: 0.1
**最終更新**: 2026-08-19
**対応 Phase**: Phase 4.9 (c)

## 1. 背景

Phase 4.9 (b) で Todo の CRUD API が復活し、5 エンドポイントが動作するようになった。ただし手動確認の過程で、入力が不正なときの挙動に 3 つの穴が見つかっている。

| # | 問題 | (b) 時点の挙動 |
|---|---|---|
| 1 | **400 のボディが空** | `priority: "URGENT"` や `title` 欠落で 400 は返るが `Content-Length: 0`。クライアントは何が悪いか分からない |
| 2 | **業務的な検証が無い** | `title: ""` や 10000 文字のタイトル、`categoryId: -1` がそのまま Service まで届く |
| 3 | **想定外の例外が裸で 500** | StatusPages に登録していない例外は Ktor 既定の 500。ボディは空で、ログにスタックトレースだけ |

1 と 3 の原因は (b) の journal に記録済み。Ktor には例外を HTTP ステータスに変換する経路が 2 系統あり、`install(StatusPages)` に登録していない例外は `io.ktor.server.engine.defaultExceptionStatusCode` が処理する。こちらは `call.respond(statusCode)` を呼ぶだけなのでボディが付かない。

2 は Spring 時代に Bean Validation が担っていた責務で、Ktor 移行時に一度失われた。[ADR 0008](../decisions/0008-migrate-from-spring-to-ktor.md) で代替として Konform を採用する方針が決まっている。

本 PR はこの 3 つを埋め、Phase 4.9 の Routing 層を完成させる。

## 2. スコープ

### この PR で作るもの

- `konform` 依存の追加（`io.konform:konform-jvm:0.11.1`）
- `validation/` パッケージの新設
  - `TodoValidation.kt` — `TodoCreateRequest` / `TodoUpdateRequest` の Konform 定義
  - `ValidationException.kt` — 検証エラーを運ぶ例外
- `dto/error/` パッケージの新設と `ErrorResponse` の移動
  - `ErrorResponse` に `fieldErrors: List<FieldError>` を追加
  - `FieldError` を新規作成
- StatusPages の拡張
  - `exception<ValidationException>` → 400 + `fieldErrors`
  - `exception<BadRequestException>` → 400（必須項目欠落のみ構造化）
  - `exception<Throwable>` → 500 のフォールバック
- `routes/TodoRoutes.kt` の POST / PUT に検証呼び出しを追加
- ADR 0016 / 0017 の追加
- `docs/architecture.md` の更新（技術スタック表、ディレクトリ表、エラー処理方針）
- journal の追加

### この PR で作らないもの

- **`testApplication` による Routing のテスト** — Phase 4.11 のテスト戦略再構築でまとめて扱う。加えて [#25](https://github.com/GenkiHashioka/kotlin-todo/issues/25) により現在テストが実行不能
- **エラーメッセージの多言語対応 / エラーコード** — [#5](https://github.com/GenkiHashioka/kotlin-todo/issues/5)。フロントエンド着手時に決める。本 PR では暫定方針の記録のみ
- **`dto/` のリソース軸分割** — Phase 5 着手時。本 PR では横断的な `error/` の分離だけ行う
- **認証 / `ownerId` の実データ化** — [#23](https://github.com/GenkiHashioka/kotlin-todo/issues/23)
- **OpenAPI へのエラースキーマ記載** — Phase 4.10
- **一覧取得の N+1 解消** — [#3](https://github.com/GenkiHashioka/kotlin-todo/issues/3)、Phase 5

## 3. 設計方針

### 判断 1: バリデーションは Presentation 層で実行する

Konform の検証対象は `TodoCreateRequest` / `TodoUpdateRequest`、すなわち **Presentation 層のクラス**である。これを Service に渡して検証させると、`architecture.md` が定めた Service 層の制約が崩れる。

> **Service 層**: **HTTP に依存しない**（`ktor.*` を import しない）

DTO は HTTP 境界のデータの形であり、Service がこれを知るべきではない。加えて、Presentation 層で弾けば DB に触る前に処理を打ち切れる。

Service 層に残る検証は「Category が存在するか」のようなドメイン不変条件で、これは (b) で既に `CategoryNotFoundException` として実装済み。**入力の形の検証（Presentation）と、ドメインの整合性の検証（Service）を層で分ける**のが本 PR の方針。

### 判断 2: 検証失敗は例外を投げて StatusPages に集約する

各ハンドラで結果を分岐させる書き方（`if (result is Invalid) { call.respond(...); return@post }`）は採らない。拡張関数が `ValidationException` を投げ、StatusPages が HTTP に変換する。

```kotlin
post {
    val request = call.receive<TodoCreateRequest>()
    request.validateOrThrow()          // 失敗なら ValidationException
    val todo = todoService.create(...)
    call.respond(HttpStatusCode.Created, todo.toResponse(...))
}
```

理由は 2 つ。

**ハンドラの形が揃う。** 検証が要るのは POST と PUT の 2 か所だが、分岐を書くと `return@post` / `return@put` がハンドラごとに散る。

**エラー整形が 1 か所に集まる。** (b) で `TodoNotFoundException` / `CategoryNotFoundException` が既に StatusPages で `ErrorResponse` に変換されている。検証失敗も同じ経路に乗せれば、レスポンスの形が食い違う余地が構造的に無くなる。

### 判断 3: Konform 定義は `validation/` パッケージに置く

```
validation/
├── TodoValidation.kt        # Validation<T> 定義 + validateOrThrow
└── ValidationException.kt
```

**`dto/` に混ぜない理由**: `dto/` を「データの**形**の定義だけ」に保つため。検証**ルール**は形ではなく振る舞いで、同居させると `dto/` の説明が「形と、あと検証も」と濁る。

**`exception/` に置かない理由**: そこにある 2 つは `TodoNotFoundException` / `CategoryNotFoundException` という**ドメインの例外**である。`ValidationException` は HTTP 境界の都合で生まれる Presentation 層の例外で、性質が違う。

`architecture.md` のディレクトリ表に 1 行増えるので、本 PR で更新する。

### 判断 4: `ErrorResponse` に `fieldErrors` を入れ子で足す

Konform は「どのフィールドがなぜ駄目か」を複数まとめて返す。(b) 時点の `ErrorResponse` は `status` と `message` しか持たず、フィールド単位の情報を載せる場所が無い。

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

**デフォルト値を付けることが要点。** 既存の 404 ハンドラ 2 つを一切変更せずにコンパイルが通り、JSON には `"fieldErrors": []` が出るだけになる。

`message` にすべて詰め込む案（`"title must not be blank, description too long"`）は採らない。クライアントが文字列を解析する羽目になり、将来フロントエンドで入力欄とエラーを対応付けられない。

返る JSON:

```json
{
  "status": 400,
  "message": "Validation failed",
  "fieldErrors": [
    { "field": "title", "message": "must not be blank" },
    { "field": "description", "message": "must be at most 2000 characters" }
  ]
}
```

フィールド名は `architecture.md` の §5 に (c) の予定として既に `fieldErrors` と書かれているため、それに合わせる。

### 判断 5: `dto/error/` を切り、横断的な型を分離する

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
    ├── LocalDateSerializer.kt
    └── LocalDateTimeSerializer.kt
```

`ErrorResponse` は他の DTO と役割が違う。`TodoResponse` は「`GET /todos/{id}` の戻り値」で特定のエンドポイントに紐づくが、`ErrorResponse` は**どのエンドポイントからも出る**し、routes ではなく **StatusPages が生成する**。

分ける根拠は「異常系だから」ではなく **「横断的だから」**。この根拠は 4 クラスの現在でも成り立ち、エンドポイントが増えても変わらない。`serializer/` が既に同じ理屈で切られているため、`error/` を並べると **`dto/` 直下 = エンドポイントの表現、サブパッケージ = 横断的な仕組み**という一貫した読み方になる。

正常系 / 異常系やサマリーといった軸での分割は採らない。理由と Phase 5 での方針は [#28](https://github.com/GenkiHashioka/kotlin-todo/issues/28) に記録済み。

本 PR で移動する理由は、どのみち `ErrorResponse` に `fieldErrors` を足すために同じファイルを触るため。別 PR にすると同じファイルを 2 回動かすことになる。

### 判断 6: 検証ルールは DB スキーマに合わせる

`V1__init.sql` と突き合わせた結果。

| フィールド | ルール | 根拠 |
|---|---|---|
| `title` | 必須 / **空白のみ不可** / 1〜200 文字 | `title VARCHAR(200) NOT NULL` |
| `description` | 任意 / 最大 2000 文字 | `description TEXT`（DB は無制限。下記参照） |
| `dueDate` | 任意 / **検証しない** | 判断 7 を参照 |
| `priority` | 必須 | kotlinx が型で保証。Konform 不要（判断 8） |
| `status` | 必須 | 同上 |
| `categoryId` | 任意 / 正の数 | 存在確認は Service の既存 404 経路 |

**`title` の空白チェック**: Konform の `minLength(1)` は `"   "` を通す（長さが 3 のため）。空白のみを弾く制約を別途書く必要がある。

**`description` の 2000 という数字に技術的根拠は無い。** `TEXT` は DB 側で無制限だが、上限の無い入力をそのまま受けると数 MB の JSON がそのまま DB に載る。これは方針の選択であり、DB より DTO を厳しくする方向なので「DTO は通るが DB で落ちる」ズレは起きない（`docs/journal/phase-04-todo-crud.md:60` の警告は逆方向のズレについてのもの）。

### 判断 7: `dueDate` に日付制約を入れない

「今日以降」制約は **Phase 4 で検討し却下済み**であり、その判断を踏襲する。

> `dueDate`に「今日以降」という制約を検討したが、PUT方式の更新では既存の（期限切れの）値がそのまま送られてくるため、更新自体が永久に失敗するという副作用に気づき、見送った
> （`docs/journal/phase-04-todo-crud.md:61`）

期限切れの Todo を「完了」にしようとした瞬間、`dueDate` が過去日であるために更新が弾かれる。PATCH による部分更新を導入するか、作成と更新で検証ルールを分けない限り解決しない。どちらも本 PR のスコープ外。

### 判断 8: enum の不正値は Konform ではなく `BadRequestException` 経路で扱う

`Priority` / `TodoStatus` は enum なので、`"URGENT"` のような不正値は **kotlinx.serialization のデシリアライズ時点で落ちる**。Konform まで到達しない。ここに制約を書いても実行されない死んだコードになる。

(b) で確認したとおり、`RequestConverter.kt:66-70` がデシリアライズ中の `Throwable` をすべて `BadRequestException` に包み直す。したがって enum 不正値への対応は、Konform ではなく `exception<BadRequestException>` のボディを埋めることで達成される。

### 判断 9: 必須項目の欠落だけ構造化し、それ以外は汎用メッセージ + ログ

kotlinx の例外メッセージを素通しにはしない。実際の文面は以下で、**内部のクラス名がそのままクライアントに出る**うえ、ライブラリのバージョンで変わる。

```
Field 'title' is required for type with serial name
'com.example.kotlin_todo.dto.TodoCreateRequest', but it was missing
```

`BadRequestException` の `cause` を見て分岐する。

```kotlin
exception<BadRequestException> { call, cause ->
    val missing = (cause.cause as? MissingFieldException)?.missingFields
    if (missing != null) {
        // 400 + fieldErrors = missing.map { FieldError(it, "is required") }
    } else {
        // 400 + message = "Malformed request body"、cause は WARN でログへ
    }
}
```

`MissingFieldException` が `missingFields: List<String>` を持つことは **1.8.1 の実体で確認済み**。

```
public final java.util.List<java.lang.String> getMissingFields();
```

これにより、`title` を送り忘れたときのレスポンスが Konform の検証失敗と**同じ形**になる。

分岐の反対側（enum 不正値、日付形式エラー）は、kotlinx の例外からフィールド名を機械的に取り出せない。ここは `"Malformed request body"` で返し、実際の原因はログに残す。クライアントに返る情報は減るが、内部構造を漏らさずバージョン変化にも強い。

### 判断 10: `exception<Throwable>` で 500 のフォールバックを置く

StatusPages に登録していない例外は Ktor 既定の 500 になり、ボディが空になる。すべてのエラーレスポンスを `ErrorResponse` の形に揃えるため、最も広い型でフォールバックを置く。

**`message` に例外の内容を入れてはならない。** スタックトレースや内部のクラス名がクライアントに漏れる。`"Internal server error"` の固定文字列を返し、`cause` は ERROR でログに残す。

### 判断 11: エラーメッセージは英語の固定文字列、エラーコードは見送る

[#5](https://github.com/GenkiHashioka/kotlin-todo/issues/5) が挙げる 2 案のうち、案 1（固定文字列）を採り、案 2（`NOT_BLANK` のような機械可読コード）は見送る。

**コードを振るなら全制約を手書きになる。** Konform の組み込み制約はメッセージを自動生成するため、コードを持たせるには全制約に `hint` を手書きする必要がある。本 PR の主目的（空ボディを埋める）に対して労力が釣り合わない。

**今はコードの粒度を決める材料が無い。** `NOT_BLANK` のような制約単位にするか `TITLE_REQUIRED` のようなフィールド込みにするかは、フロントエンドが何を表示したいかで変わる。#5 自身が「FE 着手時に決める」としている。

見送りが安全なのは、`FieldError` に `code` を後から**追加**できるため。デフォルト値付きで足せば既存のレスポンスは壊れない。

Spring 時代の「`Accept-Language` によってエラー文言の言語が勝手に変わる」問題は、Konform に移行した時点で消滅する。#5 にはその旨と本 PR での暫定方針を追記する。

### ADR に切り出す判断

| ADR | 主題 | 含める判断 |
|---|---|---|
| 0016 | バリデーションライブラリに Konform を採用する | 判断 1、3、6、7、8 |
| 0017 | エラーレスポンスの形と例外 → HTTP 変換の方針 | 判断 2、4、9、10、11 |

`architecture.md:441` が (c) の予定として 0016 / 0017 の 2 本を挙げているため、本数はそれに合わせる。切り分けの軸は「**何で検証するか**（0016）」と「**失敗をどう返すか**（0017）」。

## 4. 実装ステップ

1. `backend/build.gradle.kts` に `implementation("io.konform:konform-jvm:0.11.1")` を追加、`./gradlew build` で解決を確認
2. **Konform の実 API を確認する**（判断は下記「詰まりポイント」参照）。`ValidationResult` からエラーを取り出すプロパティ名と、空白チェックの書き方を実物で確かめる
3. `dto/error/` を作成し、`ErrorResponse.kt` を移動（IntelliJ の Refactor → Move）。`fieldErrors` を追加
4. `dto/error/FieldError.kt` を新規作成
5. `validation/ValidationException.kt` を新規作成。`List<FieldError>` を保持する
6. `validation/TodoValidation.kt` を新規作成。`Validation<TodoCreateRequest>` / `Validation<TodoUpdateRequest>` と、`validateOrThrow()` 拡張関数を実装
7. `routes/TodoRoutes.kt` の POST / PUT に `validateOrThrow()` を追加
8. `Application.kt` の StatusPages に 3 ハンドラを追加（`ValidationException` / `BadRequestException` / `Throwable`）
9. 手動確認（§5）
10. ADR 0016 / 0017 を追加、`docs/decisions/README.md` に 2 行追記
11. `docs/architecture.md` を更新（技術スタック表の Konform 行、ディレクトリ表に `validation/`、モジュール構成図、§5 エラー処理表）
12. `docs/README.md` の進捗を (c) 完了に更新
13. journal を追加
14. Issue [#5](https://github.com/GenkiHashioka/kotlin-todo/issues/5) に暫定方針を追記

## 5. 確認方法

### 起動

```bash
docker compose up -d postgres
cd backend && ./gradlew run
```

### 手動確認（正常系の回帰）

(b) で通っていたものが壊れていないことを先に確認する。

```bash
# 作成 → 201
curl -i -X POST http://localhost:8080/todos -H "Content-Type: application/json" \
  -d '{"title":"Konform を学ぶ","description":null,"dueDate":"2026-08-31","priority":"HIGH","status":"NOT_STARTED","categoryId":null}'

# 一覧 → 200、fieldErrors が出ないこと
curl -i http://localhost:8080/todos
```

### 手動確認（Konform の検証失敗 → 400 + fieldErrors）

```bash
# 空文字タイトル
curl -i -X POST http://localhost:8080/todos -H "Content-Type: application/json" \
  -d '{"title":"","description":null,"dueDate":null,"priority":"HIGH","status":"NOT_STARTED","categoryId":null}'

# 空白のみのタイトル（minLength では通ってしまうケース）
curl -i -X POST http://localhost:8080/todos -H "Content-Type: application/json" \
  -d '{"title":"   ","description":null,"dueDate":null,"priority":"HIGH","status":"NOT_STARTED","categoryId":null}'

# 201 文字のタイトル
curl -i -X POST http://localhost:8080/todos -H "Content-Type: application/json" \
  -d "{\"title\":\"$(printf 'a%.0s' {1..201})\",\"description\":null,\"dueDate\":null,\"priority\":\"HIGH\",\"status\":\"NOT_STARTED\",\"categoryId\":null}"

# 複数フィールドが同時に不正 → fieldErrors が 2 件以上返ること
curl -i -X POST http://localhost:8080/todos -H "Content-Type: application/json" \
  -d "{\"title\":\"\",\"description\":\"$(printf 'a%.0s' {1..2001})\",\"dueDate\":null,\"priority\":\"HIGH\",\"status\":\"NOT_STARTED\",\"categoryId\":null}"

# 負の categoryId
curl -i -X POST http://localhost:8080/todos -H "Content-Type: application/json" \
  -d '{"title":"ok","description":null,"dueDate":null,"priority":"HIGH","status":"NOT_STARTED","categoryId":-1}'
```

**期待**: すべて 400、`message` が `"Validation failed"`、`fieldErrors` に該当フィールドが入る。最後の 1 つは `categoryId` が `-1` でも 404 ではなく 400 になること。

### 手動確認（必須項目の欠落 → 400 + fieldErrors）

```bash
# title を送らない
curl -i -X POST http://localhost:8080/todos -H "Content-Type: application/json" \
  -d '{"description":null,"dueDate":null,"priority":"HIGH","status":"NOT_STARTED","categoryId":null}'

# title と priority を送らない → fieldErrors が 2 件
curl -i -X POST http://localhost:8080/todos -H "Content-Type: application/json" \
  -d '{"description":null,"dueDate":null,"status":"NOT_STARTED","categoryId":null}'
```

**期待**: 400、`fieldErrors` に `{"field":"title","message":"is required"}` が入る。**内部のクラス名（`com.example.kotlin_todo.dto....`）がレスポンスに現れないこと。**

### 手動確認（解析不能な入力 → 400 + 汎用メッセージ）

```bash
# 不正な enum
curl -i -X POST http://localhost:8080/todos -H "Content-Type: application/json" \
  -d '{"title":"ok","description":null,"dueDate":null,"priority":"URGENT","status":"NOT_STARTED","categoryId":null}'

# 不正な日付形式
curl -i -X POST http://localhost:8080/todos -H "Content-Type: application/json" \
  -d '{"title":"ok","description":null,"dueDate":"2026/08/31","priority":"HIGH","status":"NOT_STARTED","categoryId":null}'

# JSON として壊れている
curl -i -X POST http://localhost:8080/todos -H "Content-Type: application/json" -d '{'
```

**期待**: すべて 400、`message` が `"Malformed request body"`、`fieldErrors` は空。**サーバのログに実際の原因が WARN で残っていること。**

### 手動確認（既存の 404 が壊れていないこと）

```bash
curl -i http://localhost:8080/todos/999999
```

**期待**: 404、`fieldErrors` が空配列で出ること（`ErrorResponse` の変更が既存経路を壊していない証拠になる）。

### 自動テスト

[#25](https://github.com/GenkiHashioka/kotlin-todo/issues/25) により Testcontainers が起動せず、テストは実行不能。本 PR も (b) と同様に手動確認で代替する。

Routing のテスト（`testApplication`）は Phase 4.11 でまとめて書く。そのときに本節の curl をテストケースへ移植する。

## 6. 想定される詰まりポイント

- **懸念 A: Konform 0.11 の API 名が分からない**
  Maven Central の最新は 0.11.1（2025-03-31）でまだ 0.x。`ValidationResult` からエラーを取り出すプロパティが `dataPath` なのか `path` なのか、バージョンで変わっている可能性がある。
  → **対処**: 依存を足した直後に IntelliJ の補完か sources jar で実 API を確認してから `FieldError` への変換を書く。記憶や他バージョンの記事を根拠にしない。(b) で Ktor のソースを実際に読んで挙動を確定させたのと同じ進め方をする。

- **懸念 B: 空白のみの文字列を弾く制約が組み込みにあるか不明**
  `minLength(1)` は `"   "` を通す。Konform に `isNotBlank` 相当があるか、`addConstraint` で自前実装が要るかが未確認。
  → **対処**: 組み込みが無ければ `addConstraint("must not be blank") { it.isNotBlank() }` の形で書く。この 1 個だけメッセージが手書きになるのは許容する。

- **懸念 C: `BadRequestException` の `cause` のネストの深さ**
  判断 9 のスケッチは `cause.cause as? MissingFieldException` としているが、Ktor が例外を包む段数が 1 段とは限らない。
  → **対処**: まず素の状態でログにスタックトレースを出し、実際のネスト構造を目で見てから書く。段数が不定なら `generateSequence(cause) { it.cause }.filterIsInstance<MissingFieldException>().firstOrNull()` のように辿る。

- **懸念 D: `exception<Throwable>` が他のハンドラを飲み込む可能性**
  Ktor の StatusPages は例外のクラス階層を辿って最も具体的なハンドラを選ぶ設計のはずだが、`Throwable` を登録したことで `TodoNotFoundException` が 500 になっては本末転倒。
  → **対処**: 判断 10 を実装した直後に、必ず 404 の確認 curl（§5 の最後）を流して回帰していないことを確かめる。

- **懸念 E: `exception<Throwable>` がレスポンス書き込み中の例外まで捕まえる**
  レスポンスを書いている最中に例外が出た場合、ハンドラが再度 `call.respond` を呼んで二重応答になることがある。
  → **対処**: 正常系の回帰確認を先に流し、ログに例外が出ていないことを確認する。問題が出たら `Throwable` ではなく `Exception` に絞る、あるいは応答済みかを見て分岐する。

- **懸念 F: `ErrorResponse` の移動で import 漏れが起きる**
  `Application.kt` が `com.example.kotlin_todo.dto.ErrorResponse` を import している。
  → **対処**: IntelliJ の Refactor → Move を使えば自動追従する。手で移動しない。

- **懸念 G: `description` の 2000 文字に根拠が無い**
  DB は `TEXT` で無制限なので、この数字は方針の選択でしかない。
  → **対処**: journal に「根拠の無い数字である」と明記する。将来変更する可能性を残し、ADR 0016 では「上限を設ける」という判断だけを記録し、具体値は architecture.md 側に置く。

- **懸念 H: テストが書けないまま (c) を終えることになる**
  [#25](https://github.com/GenkiHashioka/kotlin-todo/issues/25) が解決するまで自動テストが一切走らない。手動確認の網羅性が実装の品質を直接左右する。
  → **対処**: §5 の curl をすべて実行し、結果を journal に貼る。Phase 4.11 でこれをテストコードに移植する前提で、確認項目を漏れなく書き残す。
