# kotlin-todo ドキュメント

このディレクトリは、学習しながら育てるドキュメント一式。目的に応じて、以下の文書を運用する。

## 構成

- `requirements.md` — 要件定義書。「何を作るか」の一次ソース。目的・想定ユーザー・機能要件・非機能要件・スコープ外・用語定義を記述。
- `roadmap.md` — 現在の優先順位と実装順序。Android Trackと保留中のBackend Trackを記述。
- `architecture.md` — アーキテクチャ設計。技術スタック・レイヤー構成・モジュール構造・データフロー・エラー処理フロー・トランザクション境界・テスト戦略の全体像。基本設計相当。
- `design-notes/` — Track / Phase / 機能の実装前に、責務・構成・処理方式を決める詳細設計メモ。`android/`と`backend/`に分けて配置する（[運用ガイド](design-notes/README.md)）。詳細設計相当。
- `test-plans/` — テスト実施前の観点・前提条件・手順・期待結果（[運用ガイド](test-plans/README.md)）。
- `testing/` — 実施環境・実際の結果・証拠・判定（[運用ガイド](testing/README.md)）。
- `journal/` — 実装・テスト実施後の学習ジャーナル。`android/`と`backend/`に分け、何を学び、なぜその設計にしたか、自己確認の結果を記録する。
- `decisions/` — 設計判断のみを短く切り出した記録（ADR）。ジャーナルより粒度が細かく、「なぜこの実装にしたか」を後から検索しやすくするためのもの。Phase 3から運用開始（[一覧](decisions/README.md)）。
- `db-schema.md` — DBスキーマ設計（ER図・テーブル定義・インデックス方針）。Flyway マイグレーション (`V1__init.sql`) と Exposed の Table 定義の両方の正となるドキュメント。
- `api/` — API 仕様書の見方。OpenAPI 仕様はルーティングのコードから自動生成され、リポジトリにスナップショットは置かない（[運用ガイド](api/README.md)、[ADR 0020](decisions/0020-generate-openapi-from-routing.md)）。

Android Trackの記録は、次の設計メモと学習ジャーナルから確認できる。

- Android 01: [設計メモ](design-notes/android/01-todo-list-from-api.md) / [学習ジャーナル](journal/android/01-todo-list-from-api.md)
- Android 02: [設計メモ](design-notes/android/02-todo-list-ui-state.md) / [学習ジャーナル](journal/android/02-todo-list-ui-state.md)
- Android 03: [設計メモ](design-notes/android/03-configurable-api-base-url.md) / [学習ジャーナル](journal/android/03-configurable-api-base-url.md)

- Android 04: [設計メモ](design-notes/android/04-todo-detail-navigation.md) / [学習ジャーナル](journal/android/04-todo-detail-navigation.md)
- Android 05: [設計メモ](design-notes/android/05-todo-create.md) / [学習ジャーナル](journal/android/05-todo-create.md)
- Android 06: [設計メモ](design-notes/android/06-todo-edit.md) / [学習ジャーナル](journal/android/06-todo-edit.md)
- Android 07: [設計メモ](design-notes/android/07-todo-delete.md) / [学習ジャーナル](journal/android/07-todo-delete.md)
- Android 08: [設計メモ](design-notes/android/08-todo-submit-navigation.md) / [学習ジャーナル](journal/android/08-todo-submit-navigation.md)
- Android 09: [設計メモ](design-notes/android/09-todo-success-feedback.md) / [学習ジャーナル](journal/android/09-todo-success-feedback.md)（成功結果のSnackbar表示）
- Android 10: [設計メモ](design-notes/android/10-todo-ui-packages.md) / [学習ジャーナル](journal/android/10-todo-ui-packages.md)（Todo UIの画面別パッケージ整理）
- Android 11: [テスト設計](test-plans/android/11-todo-regression-testing.md) / [学習ジャーナル](journal/android/11-todo-regression-testing.md) / [テスト実施記録](testing/android/todo-regression-results.md)（Todo機能の手動回帰テスト）

- Android 12: [実装設計](design-notes/android/12-todo-list-viewmodel-tests.md) / [テスト設計](test-plans/android/12-todo-list-viewmodel-tests.md) / [学習ジャーナル](journal/android/12-todo-list-viewmodel-tests.md) / [実施結果](testing/android/12-todo-list-viewmodel-tests.md)（Todo一覧ViewModelのローカル単体テスト）

## 運用方針

- コードは原則として開発者本人が書く。ドキュメント（journal・ADR・README等）は、AIアシスタントが下書きを生成し、開発者が事実関係と判断内容をレビュー・修正する。
- ジャーナルとADRは、実装がある程度形になった時点（フェーズ完了時）にAIアシスタントが下書きし、内容が合っているか開発者がレビューする。
- ルート`README.md`は初見の人向けの概要と起動手順、`architecture.md`は構成と責務の一次ソースとして、プロジェクトが進むごとに更新する。

- 着手前・実装後・PR前の更新手順は[AGENTS.md](../AGENTS.md#documentation-workflow)に従う。設計メモは実装前にレビューし、PR準備時には進捗・構成・要件・ロードマップの整合を確認する。

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
  - Android 03: ローカルAPIベースURLをGradle設定へ分離（完了）
  - Android 04: Todo詳細とNavigation（完了、PR #48でマージ済み）
  - Android 05: Todo登録と成功後の一覧更新（実装・手動確認済み）
  - Android 06: Todo編集と成功後の詳細・一覧更新（実装、保存成功・失敗と送信中の入力制御を手動確認。個別の確認範囲はjournal参照）
  - Android 07: 詳細からのTodo削除、一覧更新、削除中の戻る制御と通信タイムアウト（実装・手動確認。範囲と制約はjournal参照）
  - Android 08: 登録・編集の戻る制御、送信中表示、登録フォームの入力制御（実装・手動確認。範囲はjournal参照）
  - Android 09: 登録・編集・削除の成功を共通Snackbarで表示（実装・手動確認。範囲と制約はjournal参照）
  - Android 10: Todo UIを画面別パッケージへ整理（実装・主要経路を手動確認。範囲はjournal参照）
  - Android 11: 機能横断の手動回帰テストと通常構成での最終確認を実施（範囲と制約はjournal・実施記録参照）
  - Android 12: 一覧ViewModelのローカル単体テスト6件と`testDebugUnitTest`全体が成功（確認範囲と条件は実施結果参照）
  - 次: #65でUI・操作性を改善する。他画面・UI・実通信の自動テストと、結果不明時の整合回復は後続課題
- Backend Phase 5以降: フィルタ / ソート / 検索 / ページネーション等（保留）
