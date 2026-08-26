# 0020 - OpenAPI 仕様はルーティングのコードから生成し、スナップショットを持たない

**ステータス**: 採用
**日付**: 2026-08-26

## Context（背景・何を解決したいか）

Spring Boot 時代、API 仕様書は `springdoc-openapi` が `@RestController` から自動生成していた。`docs/api/README.md` はその利点をこう記録していた。

> 手書きではなく実装から生成されるため、コードとの乖離が起きない

**Ktor 移行でこれが失われた。** `docs/api/` に残っていたのは Spring 時代の遺物で、`openapi.json` は Phase 4 完了時点のスナップショット、`README.md` は既に存在しない `/swagger-ui.html` を案内していた。

加えて [#6](https://github.com/GenkiHashioka/kotlin-todo/issues/6) が「`POST /todos` は実際は 201 を返すが仕様書上は 200」「404/400 のエラーレスポンス形も未記載」を指摘したまま残っていた。

### Ktor の OpenAPI 対応は 3.3.0 で登場した

調査の結果、**当時のバージョン（3.2.0）では自動生成という選択肢がそもそも存在しなかった**。コンパイラプラグインがルーティングを解析する仕組みは Ktor 3.3.0 で追加されたもので、本 Phase は **Ktor 3.5.2 と Kotlin 2.4.10 へのアップグレードを前提とする**。

Gradle のバージョンは逆に IDE 側の制約で 9.6.0 に固定した（[ADR 0021](0021-pin-gradle-to-ide-tooling-api.md)）。

## Decision（何を決めたか）

### 1. Ktor 公式の自動生成を採用する

`io.ktor.plugin` の `openApi` を有効にし、コンパイラプラグインにルーティングを解析させる。

```kotlin
ktor {
    openApi {
        enabled = true
    }
}
```

**手書きの YAML は持たない。** 仕様の正はルーティングのコードであり、それ以外の場所に第二の正を作らない。

### 2. 推論で足りない部分だけを `describe` で補う

**境界には規則がある。「そのハンドラのコードを読んで分かることは自動、読んでも分からないことは手書き」。**

| 自動で生成される | 手で記述する |
|---|---|
| パス、HTTP メソッド、パスパラメータ | StatusPages が生成する 404 / 500 |
| リクエストボディの型（`call.receive<T>()` から） | Konform の検証失敗による 400 |
| レスポンスの型と配列の要素型（`call.respond(...)` から） | `requestBody` が必須であること |
| `201` などのステータスコード | すべての説明文 |
| `Location` ヘッダ（`call.response.header(...)` から） | |
| ルート宣言の直前の行コメント → `summary` | |

404 と 500 が自動生成に載らないのは、**エラーの生成を StatusPages に集約した [ADR 0017](0017-error-response-and-exception-mapping.md) の帰結**である。ライブラリの制約ではない。

### 3. `describe` は該当要素を丸ごと置き換える

**「不足分だけを足す」書き方は成立しない。** `requestBody` に `required = true` だけを書いたところ、推論が入れていた型が消えた。

```kotlin
requestBody { required = true }   // 型を書かなかった
```

```
required = True
content  = None                   ← 推論が入れていた TodoCreateRequest が消えた
```

したがって **`describe` に書く要素は、必要な情報をすべて書く**。型を書き直す手間が増えるが、`jsonSchema<T>()` はコンパイルされるコードなので、存在しない型名を書けばビルドが落ちる。

### 4. 共通のものは親ルートに、条件が違うものだけ操作に書く

記述の強さは **「推論 < 祖先の `describe` < 自分の `describe`」** の順である（実測で確認）。

```kotlin
route("/todos") {
    describe { /* 500。5 操作すべてに効く */ }

    get { }
    post { }.describe { /* POST 固有の 400 と 404 */ }

    route("/{id}") {
        describe { /* 400 と 404。配下の 3 操作に効く */ }

        get { }
        put { }.describe { /* PUT だけ 404 の条件が広いので上書き */ }
        delete { }
    }
}
```

**`/{id}` を入れ子ルートに再構成したのは、「3 操作だけの親」を作るため**である。`GET /todos`（一覧）は 404 を返さないので、`route("/todos")` に 404 を書くことはできない。URL は変わらない。

### 5. 正常系の `description` は空のままにする

`200` / `201` に説明文を付けない。判断 3 の全置換により、説明文を足すには型や `Location` ヘッダを手で複製することになるためである。**推論が正しく出しているものを手で書き直すのは、本 ADR の目的に反する。**

一方 `400` には必ず説明文を付ける。「id が数値でない」という発生条件は、型にも HTTP コードにも現れず、**説明文が唯一の情報源**だからである。

この判断と再検討の条件は [#37](https://github.com/GenkiHashioka/kotlin-todo/issues/37) に記録した。

### 6. 仕様の JSON は専用の設定で出力する

`kotlinx.serialization` は既定で値が `null` のフィールドも書き出す。`OpenApiDoc` は OpenAPI の全項目を網羅した型なので、**33KB の出力のうち 1688 個が `null`** という状態になった。

```kotlin
private val openApiJson = Json {
    explicitNulls = false
    prettyPrint = true
}
```

**`ContentNegotiation` の設定は変えない。** そちらを変えると `TodoResponse.description` が `null` のときにキーごと消え、**API の約束事が変わる**。仕様書を読みやすくするために本番の挙動を変えるのは順序が逆である。

### 7. スナップショットをリポジトリに持たない

`docs/api/openapi.json` を削除する。仕様は常に実行中のアプリから `/openapi.json` で取得する。

Spring 時代の運用は「実装が進むたびに手動で最新化する想定（自動化はしていない）」だったが、**この運用は実際に腐った**。#6 が指摘した「201 なのに 200 と書いてある」がその結果である。Ktor 3.5 系は静的ファイルを生成せず実行時に組み立てるため、スナップショットの更新には「起動して curl する」手作業が必要で、**腐る条件が当時とまったく同じ**である。

### 8. 生成結果をテストで守る

手書き部分が壊れても気づけない、という半自動方式の弱点に対して、`testApplication` で仕様書を取得し中身を検証する。DB には接続しない。

- 5 エンドポイントが載ること
- 404 が親ルートの `describe` から 3 操作すべてに継承されること
- `ErrorResponse` と、入れ子の `FieldError` がスキーマに登録されること
- リクエストボディの型が POST / PUT で取り違えられていないこと

## Consequences（この決定によって何が得られ、何を犠牲にしたか）

### 得られるもの

- **実装から生成されるため、正常系は乖離しない**。DTO にフィールドを足せば仕様書も追随する
- **エラーレスポンスの形が仕様書に載る**。#6 が指摘した欠落が埋まる
- **Swagger UI の「Try it out」が使える**。Phase 4.9 で curl を大量に打った作業が、ブラウザで完結する
- **手書き部分がテストで守られる**
- **ADR 0017 の設計を変えずに済む**。エラー処理は StatusPages に集約したまま

### 犠牲にするもの

- **半自動になる**。「これは自動で載るのか、書かないと載らないのか」を都度考えることになる。境界に規則はあるが、覚えておく必要がある
- **手書き部分は型で完全には守られない**。`jsonSchema<T>()` が防げるのは「存在しない型名」までで、**別の正しい型を選んでしまう間違いは防げない**。実際に `PUT` の `requestBody` に `TodoUpdateRequest` ではなく `TodoCreateRequest` と書いた。2 つは中身が同じため生成結果を見ても気づけず、`components.schemas` から `TodoUpdateRequest` が消えたことでようやく判明した
- **実験的 API に依存する**。`describe` には `@ExperimentalKtorApi` が付いており、ファイル単位の opt-in が必要（方針の統一は [#31](https://github.com/GenkiHashioka/kotlin-todo/issues/31)）
- **公式ドキュメントが実装と食い違う**。KDoc の書式も Kotlin の必要バージョンも、記載どおりでは動かなかった。**実装を読んで確かめる必要がある**
- **正常系の `description` が空になる**。OpenAPI では必須項目とされているため、形式は満たすが中身が無い
- **バージョンの制約が増える**。Ktor 3.3.0 以上と Kotlin 2.4.0 以上が必須になり、Gradle は IDE の都合で 9.6.0 に固定されている（ADR 0021）

### 代替案として検討したもの

- **手書きの `openapi.yaml` + `swaggerUI()`**：Ktor を上げずに今日から書ける最短ルート。しかし 5 エンドポイント × DTO 6 種を手で書き写すことになり、DTO を直すたび YAML も直す運用は最初に崩れる。**Spring 時代より後退する**。却下
- **`ktor-server-openapi` の静的ファイル方式**：入力が手書きファイルである点は同じで乖離が解決しない。加えて既定のレンダラは読むだけの HTML で「Try it out」が無い。却下
- **サードパーティ製ジェネレータ**：`get("/todos")` を専用 DSL に書き換えることを要求し、**ルーティングの書き方がライブラリに縛られる**。公式が自動生成を持った今、メンテナンスの継続性も弱い。却下
- **KDoc コメントで記述する**：`describe` より宣言的で、ルーティングのコードを汚さない。しかし**実測の結果、使えるのは 1 行目（`summary`）だけだった**。`Response:` 行は説明文は入るが型が反映されず、コンテンツタイプを明記しても同じだった。却下（#37 に記録）
- **スナップショットをコミットし続ける**：API の変更が PR の diff に現れる利点は実在する。しかし更新が手作業である以上、腐る条件が Spring 時代と同じ。判断 8 のテストが代替になる。却下

## 関連

- [ADR 0017 - エラーレスポンスの形を統一し、例外 → HTTP 変換を StatusPages に集約する](0017-error-response-and-exception-mapping.md) — 404 / 500 が自動生成に載らない理由
- [ADR 0021 - Gradle のバージョンを IntelliJ IDEA 同梱の Tooling API に合わせる](0021-pin-gradle-to-ide-tooling-api.md) — 本 Phase の作業中に発覚した制約
- [#6](https://github.com/GenkiHashioka/kotlin-todo/issues/6) — Spring 時代の仕様書の不正確さ
- [#37](https://github.com/GenkiHashioka/kotlin-todo/issues/37) — 正常系の `description` を補う手段の再検討
- `docs/design-notes/phase-04.10-openapi-and-swagger-ui.md` — 実装前の設計メモ
- `docs/journal/phase-04.10-openapi-and-swagger-ui.md` — 実装後の記録
