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
