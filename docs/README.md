# kotlin-todo ドキュメント

このディレクトリは、学習しながら育てるドキュメント一式。以下の3種類を運用する。

## 構成

- `journal/` — フェーズごとの学習ジャーナル。何を学び、なぜその設計にしたか、自己確認の結果を記録する。ADR（Architecture Decision Record）的な性格も兼ねる。
- `decisions/` — 設計判断のみを短く切り出した記録（ADR）。ジャーナルより粒度が細かく、「なぜこの実装にしたか」を後から検索しやすくするためのもの。Phase 3から運用開始（[一覧](decisions/README.md)）。
- `db-schema.md` — DBスキーマ設計（ER図・テーブル定義・インデックス方針）。Entity実装とFlywayマイグレーション（Phase 8）の両方の正となるドキュメント。
- `api/` — API仕様書。Phase 4でControllerが揃い始めた段階から `springdoc-openapi` を導入し、自動生成されたOpenAPI定義とその補足説明を置く。

## 運用方針

- コードは開発者本人が書く。ドキュメント（ジャーナル・ADR・README等）は、開発者がAIアシスタント（Claude Code）にプロンプトで指示し、その内容をもとにAIが下書きを生成、開発者がレビュー・修正するという運用にしている。
- ジャーナルとADRは、実装がある程度形になった時点（フェーズ完了時）にAIアシスタントが下書きし、内容が合っているか開発者がレビューする。
- README/アーキテクチャドキュメントは、ルート直下の `README.md` に集約し、プロジェクトが進むごとに更新する（現時点ではまだ雛形段階）。

## 現在の進捗

- Phase 1: Kotlin基礎（完了）
- Phase 2: Spring Boot基礎の再確認（完了）
- Phase 3: JPA/Hibernateでのドメインモデリング（完了）
- Phase 4: Todo CRUD（認証なし）（完了）
- Phase 4.5: モノレポ再編とKtor移行準備（完了）
- Phase 4.6以降: Kotlin native スタック（Ktor + Exposed + PostgreSQL）への移植（次）
- Phase 5: フィルタ/ソート/検索/ページネーション（Ktor移行完了後に再開）
