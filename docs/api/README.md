# API仕様書

OpenAPI 3.1 の仕様は、**Ktor のコンパイラプラグインが `routes/` のコードを解析して生成する**。手書きの YAML は持たない。方針は [ADR 0020](../decisions/0020-generate-openapi-from-routing.md) を参照。

## 見方

アプリを起動する。

```bash
docker compose up -d postgres   # リポジトリルート
cd backend && ./gradlew run
```

- **ブラウザで見る**: <http://localhost:8080/swagger>
  エンドポイント一覧・リクエスト/レスポンスの型・エラーの形が読める。**「Try it out」から実際にリクエストを送れる**
- **生の JSON**: <http://localhost:8080/openapi.json>
  `null` のフィールドを省き、整形した状態で返る。そのまま読める

## このディレクトリにスナップショットを置かない

**仕様の正は、実行中のアプリだけ。** リポジトリに `openapi.json` を置くと、更新が手作業になり必ず腐る。

Phase 4 までは `docs/api/openapi.json` にスナップショットを置き「実装が進むたびに手動で最新化する想定」としていたが、実際には追いつかず、[#6](https://github.com/GenkiHashioka/kotlin-todo/issues/6)（`POST /todos` は 201 を返すのに仕様書は 200）を招いた。Phase 4.10 で削除した。

代わりに、**生成結果はテストで検証する**。`backend/src/test/kotlin/com/example/kotlin_todo/routes/OpenApiSpecTest.kt` が、エンドポイントの一覧・エラーレスポンスの型・リクエストボディの型を確認している。

## 自動で載るもの、手で書くもの

**そのハンドラのコードを読んで分かることは自動、読んでも分からないことは手書き**という境界になっている。

| 自動 | 手書き（`routes/TodoRoutes.kt` の `describe`） |
|---|---|
| パス、HTTP メソッド、パスパラメータ | StatusPages が生成する 404 / 500 |
| リクエスト/レスポンスの型 | Konform の検証失敗による 400 |
| `201` などのステータスコード、`Location` ヘッダ | `requestBody` が必須であること |
| ルート宣言の直前の行コメント → `summary` | 説明文 |

404 と 500 が自動で載らないのは、エラーの生成を `Application.kt` の StatusPages に集約しているためで（[ADR 0017](../decisions/0017-error-response-and-exception-mapping.md)）、`routes/` のコードには現れないからである。

**ルート宣言の直前の行コメントは `summary` になる。** これまで自分用のメモだったものが、公開される説明文を兼ねるようになった。

## 既知の制約

- **正常系（200 / 201）の `description` が空**。`describe` は推論の結果を丸ごと置き換えるため、説明文を足すには型や `Location` ヘッダを手で複製することになる。経緯と再検討の条件は [#37](https://github.com/GenkiHashioka/kotlin-todo/issues/37)
- **認証が未実装**のため、セキュリティスキームの記載が無い（[#23](https://github.com/GenkiHashioka/kotlin-todo/issues/23)）
- **OpenAPI 生成は Ktor の実験的機能**である。Ktor のバージョンを上げる際は、生成結果が変わっていないかをテストで確認する
