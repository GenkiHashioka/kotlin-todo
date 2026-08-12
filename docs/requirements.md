# 要件定義書

**バージョン**: 0.1（Phase 4.9 (a) 完了時点）
**最終更新**: 2026-08-12

このドキュメントは「kotlin-todo で何を作るか」の一次ソース。技術的な起動手順は [README](../README.md)、実装フェーズの計画は Claude Code のローカルプラン file（リポジトリ管理外）を参照。

---

## 1. 概要

**kotlin-todo** は、個人利用の Todo 管理を題材とした **Kotlin 学習用の趣味プロジェクト**。

「Kotlin 言語そのもの（coroutines / DSL / null 安全 / 拡張関数）」と「Kotlin native な Web スタック（Ktor / Exposed / PostgreSQL）」を **実運用に近い構成で書きながら覚える** ことが主目的。副次的に、開発者本人が日常で使える Todo アプリになる。

---

## 2. 目的

### 主目的（学習）

- Kotlin 言語の書き心地を体で覚える（coroutines、DSL、null 安全、`data class`、拡張関数、scope function、`when` 式、smart cast など）
- Kotlin native な Web スタック（Ktor + Exposed + kotlinx.serialization + Konform）に慣れる
- Docker Compose / Flyway / Testcontainers など「実運用に近い開発環境」の構築を体験する
- 設計判断の記録（ADR）と学習過程の記録（journal）を **他人に見せられる形で残す** 練習

### 副目的（プロダクト）

- 開発者本人が **日常で実際に使える** Todo アプリ（趣味用途、単一ユーザー前提）
- 認証機能実装後（Phase 6 以降）は、複数ユーザーが各自独立して使える形に拡張

### 明示的に目的としないこと

- 商用サービスへの発展
- OSS として広く配布すること

---

## 3. 想定ユーザー

### 現在（Phase 4〜Phase 5 完了まで）

- **開発者本人のみ**（固定ユーザー 1 名）
- 認証機能は未実装、フロントエンドも未実装、curl / HTTP クライアントから直接利用する想定

### Phase 6 以降（認証機能実装後）

- **登録した個人ユーザー複数名**（Web ブラウザから Next.js フロントエンド経由で利用）
- 各ユーザーは自分の Todo と Category を独立して管理、他ユーザーのデータには一切アクセスしない

### 対象外ユーザー

- **チーム / 組織 / 管理者**: システムに管理者ロールは存在しない、全ユーザー同権限
- **非技術者の一般ユーザー**: UX 洗練・オンボーディング・ヘルプなどのプロダクト成熟度は追求しない
- **外部システム / API 連携**: 他サービスとの連携（カレンダー同期、Slack 通知など）は行わない

---

## 4. 機能要件

### 4.1 Todo 管理（コア機能）

**現在（Phase 4.8 まで実装済み、Phase 4.9 で HTTP 経由での操作復活予定）**:

| 機能 | 説明 |
|---|---|
| Todo 作成 | title, description, dueDate, priority, status, categoryId を指定して新規作成 |
| Todo 取得 | id 指定での 1 件取得、全件取得 |
| Todo 更新 | id 指定で全項目を新しい値に置き換える（PUT 方式、ADR 0005） |
| Todo 削除 | id 指定で削除、削除内容をレスポンスに含める（ADR 0006） |

**Phase 5 で追加予定**:

- Todo のフィルタ（priority、status、categoryId、期日範囲）
- Todo のソート（作成日、更新日、期日、優先度）
- Todo のタイトル / 説明 の LIKE 検索
- Todo のページネーション（offset + limit）

### 4.2 Category 管理

**現状**: Todo との FK 関連のみ実装、Category 単体の CRUD API は未実装。Category は開発者が直接 SQL / psql で作成する運用（学習フェーズなので簡素化）。

**Phase 5 で追加予定**:

- Category CRUD API（作成、取得、更新、削除）
- Category 削除時、関連する Todo の categoryId は自動で NULL になる（DB 側の `ON DELETE SET NULL`、ADR 0004）
- Category 名はユーザーごとに一意（ADR 0003）

### 4.3 認証 / ユーザー管理

**現状**: 認証機能は未実装。固定ユーザー 1 名（Phase 4.9 (b) の DevDataInitializer で起動時作成予定）を全リクエストの owner として扱う（ADR 0007）。

**Phase 6 で追加予定**:

- ユーザー登録（メールアドレス + パスワード）
- ログイン / ログアウト（JWT or Session Cookie、選択は Phase 6 の ADR で決める）
- 各リクエストで認証済みユーザーを owner として使用
- ユーザー削除時、関連する Todo / Category は自動で削除される（DB 側の `ON DELETE CASCADE`、ADR 0002）

### 4.4 フロントエンド

**現状**: フロントエンド無し（バックエンド API のみ）。

**Phase 6 完了後に追加予定**:

- **Next.js** による Web フロントエンド（`frontend/` サブディレクトリに配置、モノレポ構成 ADR 0009）
- Todo の一覧表示、作成、編集、削除 UI
- Category の管理 UI
- ログイン UI

### 4.5 API ドキュメント

**現状**: springdoc-openapi 撤去済み、Phase 4.10 で Ktor 流に再構築予定。

**Phase 4.10 完成時**:

- OpenAPI 定義（自動生成 or 手書き、Phase 4.10 の ADR で決定）
- Swagger UI で `http://localhost:8080/swagger-ui/` から全エンドポイントを閲覧可能

---

## 5. 非機能要件

### 5.1 開発環境の制約

- **1 人開発**、学習速度を優先
- **WSL2 (Ubuntu) + Windows 11 ホスト** で開発
- **JDK 25**（Amazon Corretto、SDKMAN 管理）、**Kotlin 2.3.21**
- **Docker Compose** で PostgreSQL 17 起動、docker daemon 生存が前提
- **IntelliJ IDEA CE**（Community Edition、無料版）をエディタとして使用

### 5.2 実装ポリシー

- **実運用に近い構成を意識**: H2 in-memory ではなく PostgreSQL + Flyway + Testcontainers、HikariCP 接続プール
- **Kotlin 言語機能をフルに活用**: `data class`、`suspend fun`、Elvis 演算子、拡張関数、scope function、type safety
- **フレームワークの魔法を最小化**: Spring の autoconfig 依存を避け、Ktor + 手動 DI で「何が起きているか」を透明化（ADR 0014）
- **学びの記録を残す**: 各 Phase 完了時に journal を書く、重要な判断は ADR に切り出す

### 5.3 品質観点

- **動作の透明性 > 抽象度**: フレームワーク経由の魔法より、自分の頭で追える設計を優先
- **型安全性**: Kotlin の nullable 型、`sealed class`、enum で「あり得ない状態」をコンパイル時に排除
- **テストの実効性**: Testcontainers で本物の PostgreSQL に対してテスト、SQL 方言差の bug を事前検出
- **schema と実装の一致**: `docs/db-schema.md` を正として、Flyway migration と Exposed Table 定義を同期させる

### 5.4 パフォーマンス

- 学習プロジェクトのため、性能目標は特に設定しない
- Ktor + Netty + coroutines の非同期モデルの恩恵は「体感」レベルで確認する程度

### 5.5 セキュリティ

- 認証機能実装まで（Phase 6 まで）、production 運用は行わない（開発機ローカル起動のみ）
- Phase 6 以降で認証を実装する際、パスワードハッシュ化、CSRF 対策、SQL injection 対策（Exposed の parameterized query で自動対応）を行う
- 現状 `application.properties` に平文パスワードがハードコード → Phase 6 で環境変数化予定

---

## 6. スコープ外（明示的に作らないもの）

### 恒久的にスコープ外

- **通知機能**: メール通知、プッシュ通知、SMS 通知など、いずれも実装しない
- **共有 / コラボレーション機能**: 他ユーザーとの Todo 共有、コメント機能、割り当て機能などは実装しない（各ユーザーは自分のデータのみ扱う）
- **チーム / 組織機能**: 組織階層、権限管理、管理者ロールなど
- **モバイルネイティブアプリ**: iOS / Android 用のネイティブアプリは作らない（Web のみ）
- **ファイル添付**: Todo にファイル / 画像を紐付ける機能
- **リマインダー機能**: 期日の 1 時間前に通知する等、通知機能と表裏一体で不採用
- **タグ機能**: 自由入力タグは実装しない、階層固定の Category で代替
- **繰り返し Todo**: 「毎週月曜」等の定期タスクは扱わない、単発 Todo のみ
- **国際化 (i18n)**: 日本語のみ、多言語切替は行わない
- **監査ログ / 操作履歴**: 誰がいつ何をしたかの詳細ログは残さない

### 将来検討の余地あり（現時点ではスコープ外）

- **全文検索 (PostgreSQL GIN / tsvector)**: Phase 5 の `LIKE` 検索が性能的に物足りなくなったら追加検討
- **サブタスク**: 1 つの Todo に子 Todo を紐付ける機能。学習ネタとして興味が湧いたら追加
- **本番デプロイ**: Kubernetes / Cloud Run / Fly.io などへのデプロイ、CI/CD 自動化。Phase 7 以降で本人が興味を持てば
- **メール通知の限定的な実装**: 認証時の確認メールなど、通知全般ではなく限定用途で必要になれば

---

## 7. 用語定義

### ドメイン用語

| 用語 | 定義 |
|---|---|
| **Todo** | 「やること」1 件を表す。id / title / description / dueDate / priority / status / categoryId / ownerId / createdAt / updatedAt を持つ |
| **Category** | Todo の分類ラベル。ユーザーごとに管理、Todo は 0 または 1 個の Category に属する（多対 1、nullable） |
| **User** | システム利用者。email / passwordHash / createdAt を持つ。Phase 6 まで固定 1 名 |
| **Owner** | Todo または Category の所有者（User）。Phase 6 の認証実装まで固定ユーザーが常に owner |
| **Priority** | Todo の優先度。`LOW` / `MEDIUM` / `HIGH` の 3 段階（`domain/Priority.kt`） |
| **TodoStatus** | Todo の進捗状態。`NOT_STARTED` / `IN_PROGRESS` / `DONE` の 3 段階（`domain/TodoStatus.kt`） |

### 技術用語

| 用語 | 定義 |
|---|---|
| **Phase** | 学習カリキュラム上の実装段階。Phase 1〜4 は Spring Boot 版、Phase 4.5 以降は Ktor 移行フェーズ、Phase 5 で本来の機能追加に復帰 |
| **ADR** | Architecture Decision Record。設計判断を短く切り出した記録。`docs/decisions/` に配置 |
| **Journal** | 各 Phase の学習記録。何を学び、なぜその設計にしたか、詰まった点、を記録。`docs/journal/` に配置 |
| **プラン file** | Claude Code の承認済み実装プラン。`~/.claude/plans/` 配下、リポジトリ管理外のローカル参照 |

---

## 8. 関連ドキュメント

- [README.md](../README.md) — プロジェクト概要と起動手順
- [docs/README.md](README.md) — ドキュメント全体の索引と運用方針
- [docs/decisions/](decisions/) — ADR（設計判断記録）一覧
- [docs/journal/](journal/) — 各 Phase の学習ジャーナル
- [docs/db-schema.md](db-schema.md) — DB スキーマ設計（`V1__init.sql` と Exposed Table 定義の正）
- [docs/api/](api/) — API 仕様書（Phase 4.10 で Ktor 版に刷新予定）
- プラン file: `~/.claude/plans/pc-springboot-kotlin-ktor-mac-giggly-key.md`（Claude Code のローカル参照）
