# 0016 - 入力バリデーションに Konform を使い、Presentation 層で実行する

**ステータス**: 採用
**日付**: 2026-08-22

## Context（背景・何を解決したいか）

Spring Boot 時代（Phase 4）は Bean Validation（`jakarta.validation`）が入力検証を担っていた。DTO のフィールドに `@NotBlank` / `@Size` を付け、Controller の引数に `@Valid` を付けると、Spring が検証を実行して失敗時に 400 を返す仕組みだった。

Ktor に移行した Phase 4.9 (b) では、この仕組みが丸ごと失われた。手動確認の結果、以下が素通りする状態になっていた。

- `title: ""`（空文字）
- `title: "   "`（空白のみ）
- 200 文字を超えるタイトル（`todos.title` は `VARCHAR(200)`。DB 挿入時に落ちる）
- `categoryId: -1`（存在しない ID として Service 層で 404 になるが、構造的には不正な入力）

[ADR 0008](0008-migrate-from-spring-to-ktor.md) で「Bean Validation → Konform」という方針は決めていたが、以下は未決だった。

1. どのバージョンをどう入れるか
2. **どの層で実行するか**（Presentation か Service か）
3. 検証定義をどこに置くか
4. 具体的にどのフィールドにどんなルールを課すか

## Decision（何を決めたか）

### 1. Konform 0.11.1 を採用する

```kotlin
implementation("io.konform:konform-jvm:0.11.1")
```

Kotlin 製の DSL 型バリデーションライブラリ。アノテーションではなく、型付きのビルダーで検証を組み立てる。

```kotlin
private val validateTodoCreateRequest = Validation<TodoCreateRequest> {
    TodoCreateRequest::title {
        notBlank()
        maxLength(200)
    }
    TodoCreateRequest::description ifPresent {
        maxLength(2000)
    }
    TodoCreateRequest::categoryId ifPresent {
        minimum(1L)
    }
}
```

### 2. 検証は Presentation 層で、Service を呼ぶ前に実行する

Konform の検証対象は `TodoCreateRequest` / `TodoUpdateRequest`、すなわち Presentation 層の DTO である。これを Service に渡して検証させると `architecture.md` が定めた Service 層の制約（**HTTP に依存しない、`ktor.*` を import しない**）が崩れる。

**層ごとに検証の性質を分ける**：

| 層 | 検証するもの | 実装 |
|---|---|---|
| Presentation | **入力の形**（空でないか、長さ、符号） | Konform |
| Service | **ドメインの整合性**（参照先が存在するか） | `CategoryNotFoundException`（(b) で実装済み） |

呼び出し位置は `call.receive()` の直後、Service 呼び出しの前。DB に触る前に打ち切る。

### 3. 検証定義は `validation/` パッケージに置く

```
validation/
├── TodoValidation.kt        # Validation<T> 定義 + validateOrThrow
└── ValidationException.kt
```

`dto/` には置かない。`dto/` を「データの**形**の定義だけ」に保つため。検証**ルール**は形ではなく振る舞いであり、同居させると `dto/` の説明が濁る。

`exception/` にも置かない。そこにある 2 つは `TodoNotFoundException` / `CategoryNotFoundException` というドメインの例外である。`ValidationException` は HTTP 境界の都合で生まれる Presentation 層の例外で、性質が違う。

**パッケージ外に公開するのは `validateOrThrow()` だけ**とし、`Validation` / `ValidationResult` / `FieldError` への変換はすべて `private` にする。これにより Konform を知っているコードが `validation/` の中だけに閉じる。

### 4. 検証ルールは DB スキーマに合わせる

| フィールド | ルール | 根拠 |
|---|---|---|
| `title` | `notBlank()` / `maxLength(200)` | `title VARCHAR(200) NOT NULL` |
| `description` | `ifPresent { maxLength(2000) }` | `description TEXT`（DB は無制限。下記参照） |
| `dueDate` | **検証しない** | 下記参照 |
| `priority` | **Konform では検証しない** | 下記参照 |
| `status` | **Konform では検証しない** | 同上 |
| `categoryId` | `ifPresent { minimum(1L) }` | 存在確認は Service の 404 経路 |

**`title` に `minLength(1)` を書かない**: `notBlank()` の実体は `constrain("must not be blank") { it.isNotBlank() }` で、空文字も空白のみも弾く。`minLength(1)` は完全に包含されるため、両方書くと `title: ""` のときに同じフィールドへ 2 件のエラーが並ぶ。Konform は失敗した制約をすべて集めて返す（最初の 1 件で止まらない）ので、冗長な制約はそのままノイズになる。

**`description` の上限 2000 に技術的根拠は無い**: `TEXT` は DB 側で無制限だが、上限の無い入力をそのまま受けると数 MB の JSON がそのまま DB に載る。これは方針の選択である。DB より DTO を厳しくする方向なので、`docs/journal/phase-04-todo-crud.md:60` が警告している「DTO は通るが DB で落ちる」ズレは起きない。

**`dueDate` に「今日以降」制約を入れない**: Phase 4 で検討し却下済みの判断を踏襲する。

> `dueDate`に「今日以降」という制約を検討したが、PUT方式の更新では既存の（期限切れの）値がそのまま送られてくるため、更新自体が永久に失敗するという副作用に気づき、見送った
> （`docs/journal/phase-04-todo-crud.md:61`）

期限切れの Todo を「完了」にしようとした瞬間、`dueDate` が過去日であるために更新が弾かれる。[ADR 0005](0005-update-uses-put-not-patch.md) で PUT 方式を採用している以上、この制約とは両立しない。

**`priority` / `status` を Konform で検証しない**: enum なので `"URGENT"` のような不正値は kotlinx.serialization のデシリアライズ時点で落ち、Konform まで到達しない。ここに制約を書いても実行されない死んだコードになる。この経路の扱いは [ADR 0017](0017-error-response-and-exception-mapping.md) に記録する。

### 5. Create と Update の検証定義は共通化しない

`TodoCreateRequest` と `TodoUpdateRequest` はフィールドが同一で、ルールも現時点では完全に一致する。それでも 2 つの `Validation<T>` を別々に書く。

共通化するには 2 つの DTO に共通のインターフェースを持たせる必要があり、それは (b) の design-note で決めた「Create と Update を統合しない」を打ち消す。加えて、**この 2 つが同じなのは偶然**である。将来 PATCH 相当の部分更新を入れれば Update 側は全フィールドが nullable になり、ルールは別物になる。

一方、Konform の結果を `FieldError` に詰め替える処理は**共通化する**。こちらが同じなのは偶然ではなく、Konform の API がひとつだからである。

**判断基準は「重複を消すか」ではなく「変更の理由が同じか」**。

## Consequences（この決定によって何が得られ、何を犠牲にしたか）

### 得られるもの

- **フィールド名がコンパイル時に検証される**: `TodoCreateRequest::title` はプロパティ参照であり、文字列ではない。フィールドをリネームすると検証定義もコンパイルエラーになる。Bean Validation の `@NotBlank` はアノテーションなので、フィールドを消しても検証が消えるだけで気づけなかった
- **ロケール依存のメッセージ問題が消滅する**: Hibernate Validator は `Accept-Language` に応じてメッセージを自動翻訳するため、クライアント次第で API のエラー文言の言語が変わっていた（[#5](https://github.com/GenkiHashioka/kotlin-todo/issues/5)）。Konform はメッセージを自前で持つので、この挙動そのものが無くなる
- **検証がどこで走るか目で追える**: Bean Validation は `@Valid` を付けると「フレームワークのどこか」で実行された。Konform は `request.validateOrThrow()` という 1 行なので、実行タイミングが読める
- **Konform への依存が 1 パッケージに閉じる**: `validation/` の外に `io.konform.*` の import が 1 つも無い。ライブラリを差し替えるとき、書き換える範囲が明確
- **層の責務が実装で担保される**: Service が DTO を受け取らないので、「HTTP に依存しない」が構造として維持される

### 犠牲にするもの

- **0.x のライブラリに依存する**: Konform の最新は 0.11.1（2025-03-31）で、まだ 1.0 に達していない。API が破壊的に変わる可能性がある。実際、`ValidationError` のパス取得は 0.x の間に何度か形が変わっている
- **検証定義が DTO から離れる**: Bean Validation は DTO のフィールド直上にルールが書かれ、1 ファイルを見れば形と制約が両方分かった。Konform では `dto/` と `validation/` の 2 ファイルを見る必要がある。パッケージの純度と引き換えの選択
- **Create / Update の重複を許容する**: 現時点では同じ内容が 2 回書かれている。片方だけ直す事故が起こりうる
- **エラーメッセージが英語の固定文字列になる**: 多言語対応は自前で作る必要がある。詳細は [ADR 0017](0017-error-response-and-exception-mapping.md) と [#5](https://github.com/GenkiHashioka/kotlin-todo/issues/5)
- **`description` の上限が恣意的**: 2000 という数字に根拠が無く、将来変更しうる

### 代替案として検討したもの

- **Service 層で検証する**: DTO ではなくドメインの値を検証する形にすれば、Presentation を経由しない呼び出し（バッチ処理など）でも検証が効く。ただし Konform に渡す型を DTO にするなら Service が DTO を知ることになり、`architecture.md` の層の制約を破る。ドメイン型に対して検証を書き直す案もあるが、「どのフィールドが不正か」をフィールド名で返せなくなる（ドメイン型はコンストラクタで組み立て済みのため、そこに至る前に落ちる）。却下
- **`init` ブロックで検証する**: `data class` の `init` で `require(title.isNotBlank())` を書けば、不正なインスタンスが存在できなくなる。型として最も強い保証だが、`IllegalArgumentException` が 1 件目で投げられるため**複数のエラーをまとめて返せない**。フォーム入力に対して 1 件ずつ直させる API になる。却下
- **手書きの検証関数**: ライブラリを入れず `if (request.title.isBlank()) errors += ...` と書く。依存が減り 0.x のリスクも無いが、制約が増えるたびに定型コードが伸びる。また「失敗を集める」「フィールド名を付ける」といった処理を自前で持つことになり、結局 Konform の劣化版を作ることになる。却下
- **Ktor の RequestValidation プラグイン**: Ktor 公式にも `install(RequestValidation)` がある。`validate<T> { }` で検証を書き、失敗時に `RequestValidationException` が飛ぶ。Ktor に閉じる利点はあるが、返せるのが `List<String>`（理由の文字列）だけで**フィールド名との対応を構造として持てない**。`fieldErrors` を返すという要件に合わない。却下

## 関連

- [ADR 0008 - Spring Boot から Ktor へ移行する](0008-migrate-from-spring-to-ktor.md) — Konform 採用の方針が決まった ADR
- [ADR 0005 - 更新は PUT 形式（全項目送信）とし、PATCH 方式は採用しない](0005-update-uses-put-not-patch.md) — `dueDate` の制約を入れられない理由
- [ADR 0017 - エラーレスポンスの形と例外 → HTTP 変換を StatusPages に集約する](0017-error-response-and-exception-mapping.md) — 検証失敗を HTTP にする側
- [#5](https://github.com/GenkiHashioka/kotlin-todo/issues/5) — エラーメッセージの言語 / コード化
- `backend/src/main/kotlin/com/example/kotlin_todo/validation/TodoValidation.kt` — 検証定義の実物
- `backend/src/main/kotlin/com/example/kotlin_todo/routes/TodoRoutes.kt` — 呼び出し位置
- `docs/design-notes/phase-04.9c-konform-and-status-pages.md` — 実装前の設計メモ
