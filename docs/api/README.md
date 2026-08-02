# API仕様書

`springdoc-openapi`により、`@RestController`のコードから自動生成されたOpenAPI 3仕様。手書きではなく実装から生成されるため、コードとの乖離が起きない。

## 見方

- **開発中にブラウザで見る**: アプリ起動後、`http://localhost:8080/swagger-ui.html`にアクセス。エンドポイント一覧・リクエスト/レスポンスの型・実際に試し打ちできる「Try it out」機能あり
- **生のJSON**: `http://localhost:8080/v3/api-docs`

## `openapi.json`について

このディレクトリの`openapi.json`は、Phase 4完了時点（Todo CRUD実装後）のOpenAPI仕様のスナップショット。実装が進むたびに手動で最新化する想定（自動化はしていない）。

再生成する場合:

```bash
./gradlew bootRun &
curl -s http://localhost:8080/v3/api-docs | python3 -m json.tool > docs/api/openapi.json
```

## 既知の制約

以下は現時点でOpenAPI仕様と実際の挙動が食い違っている、または今後改善予定の点。詳細はGitHub Issueを参照。

- [#6 OpenAPI仕様書のステータスコード/エラーレスポンスが不正確](https://github.com/GenkiHashioka/kotlin-todo/issues/6) — 例: `POST /todos`は実際は201を返すが仕様書上は200と記載される。404/400のエラーレスポンス形も未記載
