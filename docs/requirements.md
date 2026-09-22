# 要件定義書

**バージョン**: 0.4（AndroidローカルAPI設定の外部化時点）
**最終更新**: 2026-09-22

このドキュメントは「kotlin-todoで何を作るか」の一次ソース。技術的な起動手順は[README](../README.md)、実装順序は[roadmap.md](roadmap.md)を参照。

---

## 1. 概要

**kotlin-todo**は、個人利用のTodo管理を題材とした**Kotlin / Android学習用の趣味プロジェクト**。

既存のKtorバックエンドをAPI基盤として利用し、Jetpack ComposeによるAndroidクライアントを段階的に構築する。Kotlin、Compose、Coroutines、Flow / StateFlow、ViewModel、Android Architectureを、実際に機能を作りながら理解することが現在の主目的である。副次的に、開発者本人が日常で使えるTodoアプリになることを目指す。

---

## 2. 目的

### 主目的（学習）

- Kotlinの基本構文とCoroutines / Flowを実装の中で定着させる
- Jetpack Compose、ViewModel、StateFlow、Repositoryを用いたAndroidアプリの設計と実装を経験する
- AndroidクライアントからKtor API、PostgreSQLまでのデータフローを理解する
- 必要性が生じた段階でNavigation、DI、テストを追加し、導入理由を説明できるようにする
- 設計判断（ADR）と学習過程（journal）を他人に見せられる形で残す
- 既存のKtor / Exposed / PostgreSQLバックエンドは、Androidに必要な範囲で保守する

### 副目的（プロダクト）

- 開発者本人が **日常で実際に使える** Todo アプリ（趣味用途、単一ユーザー前提）
- 認証機能を実装する段階では、複数ユーザーが各自独立して使える形に拡張

### 明示的に目的としないこと

- 商用サービスへの発展
- OSS として広く配布すること

---

## 3. 想定ユーザー

### 現在

- **開発者本人のみ**（固定ユーザー 1 名）
- 認証機能は未実装
- Androidクライアントまたはcurl / HTTPクライアントから利用する

### 認証機能実装後

- **登録した個人ユーザー複数名**（Androidクライアントから利用）
- 各ユーザーは自分の Todo と Category を独立して管理、他ユーザーのデータには一切アクセスしない

### 対象外ユーザー

- **チーム / 組織 / 管理者**: システムに管理者ロールは存在しない、全ユーザー同権限
- **非技術者の一般ユーザー**: UX 洗練・オンボーディング・ヘルプなどのプロダクト成熟度は追求しない
- **外部システム / API 連携**: 他サービスとの連携（カレンダー同期、Slack 通知など）は行わない

---

## 4. 機能要件

### 4.1 Todo 管理（コア機能）

**現在（Ktor APIで実装済み）**:

| 機能 | 説明 |
|---|---|
| Todo 作成 | title, description, dueDate, priority, status, categoryId を指定して新規作成 |
| Todo 取得 | id 指定での 1 件取得、全件取得 |
| Todo 更新 | id 指定で全項目を新しい値に置き換える（PUT 方式、ADR 0005） |
| Todo 削除 | id 指定で削除、削除内容をレスポンスに含める（ADR 0006） |

**Backend機能拡張を再開したときに検討**:

- Todo のフィルタ（priority、status、categoryId、期日範囲）
- Todo のソート（作成日、更新日、期日、優先度）
- Todo のタイトル / 説明 の LIKE 検索
- Todo のページネーション（offset + limit）

### 4.2 Category 管理

**現状**: Todo との FK 関連のみ実装、Category 単体の CRUD API は未実装。Category は開発者が直接 SQL / psql で作成する運用（学習フェーズなので簡素化）。

**Backend機能拡張を再開したときに検討**:

- Category CRUD API（作成、取得、更新、削除）
- Category 削除時、関連する Todo の categoryId は自動で NULL になる（DB 側の `ON DELETE SET NULL`、ADR 0004）
- Category 名はユーザーごとに一意（ADR 0003）

### 4.3 認証 / ユーザー管理

**現状**: 認証機能は未実装。`DevDataInitializer`が起動時に用意する固定ユーザー1名を全リクエストのownerとして扱う（ADR 0007）。

**将来追加予定**:

- ユーザー登録（メールアドレス + パスワード）
- ログイン / ログアウト（JWT or Session Cookie。実装時にADRで選択を記録する）
- 各リクエストで認証済みユーザーを owner として使用
- ユーザー削除時、関連する Todo / Category は自動で削除される（DB 側の `ON DELETE CASCADE`、ADR 0002）

### 4.4 Androidクライアント

**現状**:

- Jetpack ComposeによるAndroidプロジェクトを`android/`に配置
- `GET /todos`を呼び出し、Todoタイトルを一覧表示
- `Compose → ViewModel → StateFlow → Repository → Retrofit → Ktor API`のデータフローを構築
- Todo一覧のLoading / Success / Empty / Errorを表示
- 通信失敗時にTodo一覧の取得を再試行
- ローカルAPIベースURLをGit管理対象のKotlinコードから分離

**今後追加するもの**:

- Todo詳細、作成、編集、削除
- Navigation
- DI
- ViewModel / Repository / UIのテスト

Next.jsによるWebフロントエンドは現在の計画から延期し、Androidクライアントを優先する。方針変更は[ADR 0022](decisions/0022-prioritize-android-client.md)を参照。

### 4.5 API ドキュメント

**現状**:

- KtorのルーティングコードからOpenAPI定義を生成
- Swagger UIを`http://localhost:8080/swagger`で提供
- OpenAPI JSONを`http://localhost:8080/openapi.json`で提供
- 生成方針はADR 0020に記録

---

## 5. 非機能要件

### 5.1 開発環境の制約

- **1 人開発**、学習速度を優先
- **WSL2 (Ubuntu) + Windows 11ホスト**で開発
- BackendはWSL2、Android StudioとAndroid EmulatorはWindows側で実行
- Backendは**JDK 25**（Amazon Corretto、SDKMAN管理）と**Kotlin 2.4.10**を使用
- Androidは**Kotlin 2.2.10**を使用し、Android StudioのGradle JDKでビルド
- **Docker Compose** で PostgreSQL 17 起動、docker daemon 生存が前提
- BackendはIntelliJ IDEA CE、AndroidはAndroid Studioを使用
- AndroidのローカルAPIベースURLはWindowsユーザーのGradle User Homeで設定し、`BuildConfig`経由でアプリへ渡す

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

- 認証機能を実装するまでproduction運用は行わない（開発機ローカル起動のみ）
- 認証を実装する際、パスワードハッシュ化、CSRF対策、SQL injection対策（Exposedのparameterized queryで対応）を行う
- 現状はDB接続情報がソースコードにハードコードされているため、本番運用を検討する段階で環境変数などへ外部化する

---

## 6. スコープ外（明示的に作らないもの）

### 恒久的にスコープ外

- **通知機能**: メール通知、プッシュ通知、SMS 通知など、いずれも実装しない
- **共有 / コラボレーション機能**: 他ユーザーとの Todo 共有、コメント機能、割り当て機能などは実装しない（各ユーザーは自分のデータのみ扱う）
- **チーム / 組織機能**: 組織階層、権限管理、管理者ロールなど
- **iOSアプリ**: 現在の学習対象はKotlin / Androidであり、iOSネイティブアプリは作らない
- **Next.js Webフロントエンド**: Androidクライアントの主要機能が完成するまで延期する
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
| **User** | システム利用者。email / passwordHash / createdAt を持つ。認証実装までは固定1名 |
| **Owner** | TodoまたはCategoryの所有者（User）。認証実装までは固定ユーザーが常にowner |
| **Priority** | Todo の優先度。`LOW` / `MEDIUM` / `HIGH` の 3 段階（`domain/Priority.kt`） |
| **TodoStatus** | Todo の進捗状態。`NOT_STARTED` / `IN_PROGRESS` / `DONE` の 3 段階（`domain/TodoStatus.kt`） |

### 技術用語

| 用語 | 定義 |
|---|---|
| **Backend Phase** | Spring Boot版からKtor版への移行を管理していた従来の実装段階。Phase 4.11以降は保留中 |
| **Android Track** | 2026-09以降の主な開発系列。Todo一覧から始め、CRUD、状態管理、Navigation、DI、テストへ段階的に進む |
| **ADR** | Architecture Decision Record。設計判断を短く切り出した記録。`docs/decisions/` に配置 |
| **Journal** | 各 Phase の学習記録。何を学び、なぜその設計にしたか、詰まった点、を記録。`docs/journal/` に配置 |

---

## 8. 関連ドキュメント

- [README.md](../README.md) — プロジェクト概要と起動手順
- [docs/roadmap.md](roadmap.md) — 現在の優先順位と実装順序
- [docs/README.md](README.md) — ドキュメント全体の索引と運用方針
- [docs/decisions/](decisions/) — ADR（設計判断記録）一覧
- [docs/journal/](journal/) — 各 Phase の学習ジャーナル
- [docs/db-schema.md](db-schema.md) — DB スキーマ設計（`V1__init.sql` と Exposed Table 定義の正）
- [docs/api/](api/) — Ktorが生成するOpenAPI仕様とSwagger UIの利用方法
