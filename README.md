# kotlin-todo

Kotlin を学習しながら育てる Todo アプリ。バックエンド + 将来のフロントエンドをモノレポ構成で扱う趣味プロジェクト。

## 技術スタック

- Kotlin 2.4 / JDK 25
- Ktor 3.5（HTTP サーバ、Netty エンジン、OpenAPI 仕様の生成）
- Exposed 0.61（Kotlin 製 SQL DSL、DAO ではなく DSL API を採用）
- PostgreSQL 17（開発環境は Docker Compose、統合テストは Testcontainers）
- Flyway 11（スキーマ移行） / HikariCP 6（コネクションプール）
- kotlinx.serialization（JSON） / Konform 0.11（バリデーション） / Logback 1.5
- JUnit 5 + kotlin-test-junit5 + ktor-server-test-host

Phase 4.6〜4.9 で Spring Boot + JPA/Hibernate 版から Kotlin native なスタックに移行済み。移行前の実装は `v0.4-spring-final` タグで保全。

## 現在の進捗

Phase 4.10 完了。Todo の CRUD API・入力バリデーション・OpenAPI 仕様の自動生成が動作する。次はテスト戦略の再構築（Phase 4.11）。

フェーズごとの詳細は [`docs/README.md`](docs/README.md) 参照。

## 構成

```
kotlin-todo/
├── backend/            # Ktor + Exposed バックエンド
├── docs/               # 要件 / アーキテクチャ / 設計メモ / 学習ジャーナル / ADR
└── docker-compose.yml  # PostgreSQL（開発用）
```

## 起動

```bash
# PostgreSQL 起動
docker compose up -d postgres

# Backend 起動
cd backend
./gradlew run
```

デフォルト http://localhost:8080 で待ち受け。動作確認:

```bash
curl http://localhost:8080/health
```

Todo の CRUD API も利用可能:

```bash
# 作成
curl -X POST http://localhost:8080/todos -H "Content-Type: application/json" -d '{"title":"Ktor を学ぶ","description":null,"dueDate":"2026-08-31","priority":"HIGH","status":"NOT_STARTED","categoryId":null}'

# 一覧
curl http://localhost:8080/todos
```

エンドポイントは `GET /todos`, `GET /todos/{id}`, `POST /todos`, `PUT /todos/{id}`, `DELETE /todos/{id}` の 5 つ。認証は未実装のため、`ownerId` には起動時に用意される開発用の固定ユーザーが入る（[#23](https://github.com/GenkiHashioka/kotlin-todo/issues/23)）。

入力が不正な場合は、どのフィールドがなぜ駄目かを返す:

```bash
curl -X POST http://localhost:8080/todos -H "Content-Type: application/json" \
  -d '{"title":"","description":null,"dueDate":null,"priority":"HIGH","status":"NOT_STARTED","categoryId":null}'
```

```json
{"status":400,"message":"Validation failed","fieldErrors":[{"field":"title","message":"must not be blank"}]}
```

エラーレスポンスは 400 / 404 / 500 のすべてがこの形（`status` / `message` / `fieldErrors`）で返る。詳細は [ADR 0016](docs/decisions/0016-konform-for-validation.md)（バリデーション）と [ADR 0017](docs/decisions/0017-error-response-and-exception-mapping.md)（エラー変換）を参照。

API 仕様書はブラウザから <http://localhost:8080/swagger> で読める。エンドポイント一覧・リクエスト/レスポンスの型・エラーの形が並び、**「Try it out」から実際にリクエストを送れる**。生の JSON は <http://localhost:8080/openapi.json>。

仕様はルーティングのコードから自動生成されるため、リポジトリにスナップショットは置いていない（[ADR 0020](docs/decisions/0020-generate-openapi-from-routing.md) / [見方](docs/api/README.md)）。

IntelliJ IDEA を使う場合は、リポジトリのルートを開き、`backend/build.gradle.kts` を Gradle プロジェクトとしてリンクする（docs も同じウィンドウで扱えるため）。起動は Run configuration から。

**Gradle は 9.6.0 に固定している。** IntelliJ IDEA 2026.2.1 が同梱する Tooling API が 9.6.0 であり、9.7 系にすると IDE の Gradle 同期がエラーを出さないまま壊れる（[ADR 0021](docs/decisions/0021-pin-gradle-to-ide-tooling-api.md)）。

## テスト実行

```bash
cd backend
./gradlew test
```

統合テストは Testcontainers で PostgreSQL コンテナを都度起動するため、Docker が動いていれば追加準備は不要。

Docker Engine 29 以降は API バージョン 1.40 未満のクライアントを拒否するため、`build.gradle.kts` で Testcontainers が使う API バージョンを明示している（[ADR 0018](docs/decisions/0018-pin-docker-api-version-for-testcontainers.md)）。

## ドキュメント

- [`docs/requirements.md`](docs/requirements.md) — 要件定義書（何を作るか）
- [`docs/architecture.md`](docs/architecture.md) — アーキテクチャ設計（どう作るか）
- [`docs/README.md`](docs/README.md) — docs 全体の index（設計判断（ADR）・学習ジャーナル・DB スキーマ・design-notes など）
