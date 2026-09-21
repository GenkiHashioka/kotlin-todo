# kotlin-todo ドキュメント

このディレクトリは、学習しながら育てるドキュメント一式。以下の3種類を運用する。

## 構成

- `requirements.md` — 要件定義書。「何を作るか」の一次ソース。目的・想定ユーザー・機能要件・非機能要件・スコープ外・用語定義を記述。
- `roadmap.md` — 現在の優先順位と実装順序。Android Trackと保留中のBackend Trackを記述。
- `architecture.md` — アーキテクチャ設計。技術スタック・レイヤー構成・モジュール構造・データフロー・エラー処理フロー・トランザクション境界・テスト戦略の全体像。基本設計相当。
- `design-notes/` — Track / Phase / 機能実装前の詳細設計メモ。`android/`と`backend/`に分けて配置する（[運用ガイド](design-notes/README.md)）。詳細設計相当。
- `journal/` — 実装後の学習ジャーナル。`android/`と`backend/`に分け、何を学び、なぜその設計にしたか、自己確認の結果を記録する。
- `decisions/` — 設計判断のみを短く切り出した記録（ADR）。ジャーナルより粒度が細かく、「なぜこの実装にしたか」を後から検索しやすくするためのもの。Phase 3から運用開始（[一覧](decisions/README.md)）。
- `db-schema.md` — DBスキーマ設計（ER図・テーブル定義・インデックス方針）。Flyway マイグレーション (`V1__init.sql`) と Exposed の Table 定義の両方の正となるドキュメント。
- `api/` — API 仕様書の見方。OpenAPI 仕様はルーティングのコードから自動生成され、リポジトリにスナップショットは置かない（[運用ガイド](api/README.md)、[ADR 0020](decisions/0020-generate-openapi-from-routing.md)）。

Android Trackの記録は、次の設計メモと学習ジャーナルから確認できる。

- Android 01: [設計メモ](design-notes/android/01-todo-list-from-api.md) / [学習ジャーナル](journal/android/01-todo-list-from-api.md)
- Android 02: [設計メモ](design-notes/android/02-todo-list-ui-state.md) / [学習ジャーナル](journal/android/02-todo-list-ui-state.md)

## 運用方針

- コードは原則として開発者本人が書く。ドキュメント（journal・ADR・README等）は、AIアシスタントが下書きを生成し、開発者が事実関係と判断内容をレビュー・修正する。
- ジャーナルとADRは、実装がある程度形になった時点（フェーズ完了時）にAIアシスタントが下書きし、内容が合っているか開発者がレビューする。
- ルート`README.md`は初見の人向けの概要と起動手順、`architecture.md`は構成と責務の一次ソースとして、プロジェクトが進むごとに更新する。

## 現在の進捗

- Phase 1: Kotlin基礎（完了）
- Phase 2: Spring Boot基礎の再確認（完了）
- Phase 3: JPA/Hibernateでのドメインモデリング（完了）
- Phase 4: Todo CRUD（認証なし）（完了）
- Phase 4.5: モノレポ再編とKtor移行準備（完了）
- Phase 4.6以降: Kotlin native スタックへの移植（DB 先行の順序で進行、[ADR 0010](decisions/0010-db-migration-before-framework-swap.md) 参照）
  - Phase 4.6: DB 移行（Spring + JPA のまま H2 → PostgreSQL + Flyway + Testcontainers 化）（完了）
  - Phase 4.7: Ktor 骨組み + `/health`（Spring 削除、`build.gradle.kts` 化）（完了）
  - Phase 4.8: Exposed でデータアクセス層（完了）
  - Phase 4.9: Ktor Routing + DTO + Service + StatusPages + Konform（CRUD API 復活）
    - (a) 設計整理 + architecture.md 整備（完了）
    - (b) Routing + DTO（完了、CRUD API が動作）
    - (c) Konform バリデーション + StatusPages 拡張（完了、不正な入力に正しく応答する）
  - Phase 4.10: OpenAPI / Swagger UI 再構築（完了、ルーティングのコードから自動生成 + Swagger UI）
  - Phase 4.11: テスト戦略再構築（Ktor + Exposed版）（Android優先のため保留）
- Android Track（現在の主な開発対象、[ADR 0022](decisions/0022-prioritize-android-client.md)）
  - Android 01: Ktor APIからTodo一覧を取得し、Composeで表示（完了）
  - Android 02: Todo一覧のLoading / Success / Empty / Errorと再試行（完了）
  - 次: Todo詳細
- Backend Phase 5以降: フィルタ / ソート / 検索 / ページネーション等（保留）
