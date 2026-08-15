# kotlin-todo

Kotlin を学習しながら育てる Todo アプリ。バックエンド + 将来のフロントエンドをモノレポ構成で扱う趣味プロジェクト。

## 技術スタック

- Kotlin 2.3 / JDK 25
- Ktor 3.2（HTTP サーバ、Netty エンジン）
- Exposed 0.61（Kotlin 製 SQL DSL、DAO ではなく DSL API を採用）
- PostgreSQL 17（開発環境は Docker Compose、統合テストは Testcontainers）
- Flyway 11（スキーマ移行） / HikariCP 6（コネクションプール）
- kotlinx.serialization（JSON） / Logback 1.5
- JUnit 5 + kotlin-test-junit5

Phase 4.6〜4.9 で Spring Boot + JPA/Hibernate 版から Kotlin native なスタックに移行済み。移行前の実装は `v0.4-spring-final` タグで保全。

## 現在の進捗

Phase 4.9 (a) 完了。Ktor 移行のうち Repository / Service 層まで実装済みで、Routing 層（Phase 4.9 (b)）を実装中。

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

CRUD API は Phase 4.9 (b) で復活予定。OpenAPI / Swagger UI は Phase 4.10 で再構築予定。

IntelliJ IDEA を使う場合は `backend/` をプロジェクトとして開いて Run configuration から起動する方が楽。

## テスト実行

```bash
cd backend
./gradlew test
```

統合テストは Testcontainers で PostgreSQL コンテナを都度起動するため、Docker が動いていれば追加準備は不要。

## ドキュメント

- [`docs/requirements.md`](docs/requirements.md) — 要件定義書（何を作るか）
- [`docs/architecture.md`](docs/architecture.md) — アーキテクチャ設計（どう作るか）
- [`docs/README.md`](docs/README.md) — docs 全体の index（設計判断（ADR）・学習ジャーナル・DB スキーマ・design-notes など）
