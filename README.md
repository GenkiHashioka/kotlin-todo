# kotlin-todo

Kotlin を学習しながら育てる Todo アプリ。バックエンド + 将来のフロントエンドをモノレポ構成で扱う趣味プロジェクト。

## 現在のフェーズ

Phase 4.5 完了。Spring Boot 実装（Phase 1〜4）は `v0.4-spring-final` タグで保全済み、Kotlin native なスタック（Ktor + Exposed + PostgreSQL）への移行準備が整った状態。Phase 4.6 以降で実装移植を進める。

## 構成

- `backend/` — Kotlin バックエンド（現在は Spring Boot 4.1、Ktor へ移行予定）
- `docs/` — 設計判断（[ADR](docs/decisions/)）・学習ジャーナル（[journal](docs/journal/)）・[DB スキーマ](docs/db-schema.md)・[API 仕様](docs/api/)
- `docker-compose.yml` — PostgreSQL（開発用、Phase 4.6 以降で本格利用）

## 起動

```bash
# PostgreSQL 起動
docker compose up -d postgres

# Backend 起動（要: JDK 25）
cd backend
./gradlew bootRun
```

IntelliJ IDEA を使う場合は `backend/` をプロジェクトとして開いて Run configuration から起動する方が楽。

デフォルト http://localhost:8080。API 仕様は http://localhost:8080/swagger-ui.html （起動中）を参照。

## ドキュメント

詳細は [docs/](docs/) を参照。
