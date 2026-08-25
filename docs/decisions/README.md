# 設計判断記録（ADR）

実装の過程で発生した「なぜこの実装にしたか」という判断を、短く切り出して記録する。学習ジャーナル（`docs/journal/`）より粒度が細かく、後から特定の判断だけを検索しやすくするためのもの。

各ファイルはテンプレート（`TEMPLATE.md`）に沿って、番号順に追加していく。

## 一覧

| # | タイトル |
|---|---|
| 0001 | [enumはORDINALではなくSTRINGでDB保存する](0001-enum-storage-as-string.md) |
| 0002 | [Userを削除したら関連するTodo/CategoryもCASCADE削除する](0002-cascade-delete-on-user.md) |
| 0003 | [Category名はユーザーごとに一意にする](0003-category-name-unique-per-owner.md) |
| 0004 | [Categoryを削除したら、それに紐づくTodoのcategoryはnullにする](0004-category-delete-sets-todo-category-null.md) |
| 0005 | [更新はPUT形式（全項目送信）とし、PATCH方式は採用しない](0005-update-uses-put-not-patch.md) |
| 0006 | [DELETE成功時は204ではなく200+削除内容を返す](0006-delete-returns-200-with-body.md) |
| 0007 | [認証機能ができるまでは固定ユーザーで代用する](0007-fixed-user-until-auth-exists.md) |
| 0008 | [Spring Boot から Ktor へ移行する](0008-migrate-from-spring-to-ktor.md) |
| 0009 | [モノレポ構成（backend/ と将来の frontend/）を採用する](0009-monorepo-structure.md) |
| 0010 | [DB 移行（PostgreSQL + Flyway + Testcontainers 化）を Web フレームワーク移行より先に行う](0010-db-migration-before-framework-swap.md) |
| 0011 | [スキーマ管理を Flyway に集約する（`ddl-auto=none`）](0011-flyway-for-schema-management.md) |
| 0012 | [統合テストで Testcontainers（実 PostgreSQL コンテナ）を使う](0012-testcontainers-for-integration-test.md) |
| 0013 | [Gradle スクリプトを Groovy DSL から Kotlin DSL に切り替える](0013-kotlin-dsl-gradle.md) |
| 0014 | [依存注入は手動 DI で行い、Koin を採用しない](0014-manual-di-over-koin.md) |
| 0015 | [Exposed の DAO API ではなく DSL API を採用する](0015-exposed-dsl-over-dao.md) |
| 0016 | [入力バリデーションに Konform を使い、Presentation 層で実行する](0016-konform-for-validation.md) |
| 0017 | [エラーレスポンスの形を統一し、例外 → HTTP 変換を StatusPages に集約する](0017-error-response-and-exception-mapping.md) |
| 0018 | [Testcontainers が使う Docker API バージョンを 1.44 に固定する](0018-pin-docker-api-version-for-testcontainers.md) |
| 0019 | [Pull Request のマージは squash に統一する](0019-squash-merge-for-pull-requests.md) |
| 0021 | [Gradle のバージョンを IntelliJ IDEA 同梱の Tooling API に合わせる](0021-pin-gradle-to-ide-tooling-api.md) |
