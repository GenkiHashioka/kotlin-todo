# 0022 - Androidクライアントを主要な開発対象にする

**ステータス**: 採用
**日付**: 2026-09-21

## Context（背景・何を解決したいか）

Ktorへの移行とTodo CRUD、入力検証、OpenAPI生成までが完了し、次はBackend Phase 4.11としてテスト戦略を再構築する予定だった。一方、現在の業務ではKotlin / Jetpack ComposeによるAndroid開発に参加しており、2027年中にKotlin / Androidを主軸とする役割を担える技術力を身につけることを目標としている。

業務では既存Androidコードの調査・修正・テストを経験できるが、アプリをゼロから設計し、データ取得から画面表示まで組み立てる経験は個人開発で補う必要がある。Backendを完成させてからクライアントへ進む計画を続けると、現在の目標に直結するAndroidの実装開始が遅れる。

既存文書では、Phase 4.11、Backend機能拡張、認証、Next.jsフロントエンドの順に進む前提だった。この前提を現在の学習目標に合わせて見直す必要がある。

## Decision（何を決めたか）

2027年前半まで、`android/`のAndroidクライアントを主な開発対象にする。

- 既存Ktorバックエンドは、Androidが利用する安定したAPI基盤として維持する
- Backend Phase 4.11と以降の機能拡張は中止せず保留する
- Jetpack ComposeでTodo一覧、詳細、CRUDを段階的に実装する
- ViewModel、StateFlow、Repository、Navigation、DI、テストを、必要性が生じる順に学ぶ
- AndroidとBackendは同じリポジトリに置くモノレポ構成を維持する
- Next.jsフロントエンドは現在の計画から延期する
- Backendの変更は、Androidの要件、重大な不具合、保守上必要な対応に絞る

最初の縦断的な機能として、次のデータフローで`GET /todos`の一覧を表示する。

```text
Compose
→ ViewModel
→ StateFlow / UI State
→ Repository
→ Retrofit
→ Ktor API
→ PostgreSQL
```

## Consequences（この決定によって何が得られ、何を犠牲にしたか）

### 得られるもの

- 現在の業務経験と個人学習をKotlin / Androidへ集中できる
- 既存コードの修正だけでなく、Androidアプリをゼロから設計する経験を得られる
- 既に作成したKtor APIとPostgreSQLを再利用し、Androidの学習をすぐに開始できる
- AndroidからBackend、Databaseまで1つのプロダクトとして説明できる
- 必要性から逆算してArchitecture、DI、テストを学べる

### 犠牲にするもの

- Backend Phase 4.11で予定していた統合テストの整備が遅れる
- Backendの既知課題と機能拡張が未完了のまま残る
- このリポジトリでNext.jsクライアントを設計・実装する機会は当面先送りになる
- Windows、WSL2、Android Emulatorをまたぐ開発環境の複雑さを受け入れる必要がある
- Androidの要件によっては、保留中のBackendへ一時的に戻る必要がある

別プロジェクトではNext.jsのコードレビューと必要に応じたIssue対応を継続するため、Webフロントエンドの経験が完全に途切れるわけではない。本ADRで変更するのは、このリポジトリにおける主な学習・実装対象である。

## 代替案として検討したもの

### Backendを完成させてからAndroidへ進む

Phase 4.11、フィルタ・検索、認証まで終えてからAndroidへ進む案。Backendの完成度は上がるが、Androidの設計・実装経験を得る時期が遅れ、現在のキャリア目標との優先順位が合わないため採用しない。

### Next.jsフロントエンドを先に作る

当初のrequirementsとADR 0009に沿う案。Web開発経験は得られるが、現在優先するKotlin / Androidの成長へ直接つながりにくいため延期する。

### Androidを別リポジトリに分ける

Windows側でAndroid、WSL2側でBackendを扱いやすい。一方で、API契約、Issue、ADR、履歴を1つのプロダクトとして追跡しにくくなる。物理的にはOSごとにcloneを分け、論理的にはモノレポを維持する方針を採る。

### Linux版Android StudioをWSL2で使用する

AndroidプロジェクトとBackendを同じWSL2上で扱える利点がある。一方、手元の開発環境ではWindows上のAndroid Emulatorを使用するため、WSLgによるIDE表示、Windows側EmulatorとのADB接続、GPU・仮想化をまたぐ構成の確認が別途必要になる。現在の目的はAndroidアプリの設計と実装であり、開発環境自体の検証へ時間を広げないため、Android StudioとAndroidプロジェクトはWindows側へ置く。

これはLinux版Android Studioが利用できないという判断ではない。Windows版Android StudioとWindows上のEmulatorを、現時点で安定して動作確認できた構成として採用した判断である。

## ADR 0009との関係

[ADR 0009](0009-monorepo-structure.md)の「Backendとクライアントを同じリポジトリに置く」という判断は維持する。「将来のクライアントをNext.jsとする」「Phase 6完了後に追加する」という部分を本ADRで更新し、現在のクライアントを`android/`とする。

## 関連

- [プロジェクトロードマップ](../roadmap.md)
- [要件定義書](../requirements.md)
- [アーキテクチャ設計](../architecture.md)
- [ADR 0009 - モノレポ構成を採用する](0009-monorepo-structure.md)
- [Android Todo一覧のdesign note](../design-notes/android/01-todo-list-from-api.md)
