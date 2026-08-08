# 0009 - モノレポ構成（backend/ と将来の frontend/）を採用する

**ステータス**: 採用
**日付**: 2026-08-08

## Context（背景・何を解決したいか）

Phase 6（認証・複数ユーザー）完了後にフロントエンド（Next.js）を追加する予定になった。バックエンド（Ktor）とフロントエンド（Next.js）をどのようなリポジトリ構造で扱うかを、フロント着手前の Phase 4.5（再編とインフラ準備）の段階で決める必要があった。

作品として「バックエンドとフロントエンドが1つのプロダクトとして繋がっている」全体像を、リポジトリを開いた時にひと目で伝わる形にしたい。

## Decision（何を決めたか）

**単一リポジトリ内に `backend/` と `frontend/` を並置するモノレポ構成**を採用する。当面は `backend/` のみが存在し、Phase 6 完了時点で `frontend/` を追加する。

```
kotlin-todo/                # モノレポルート
├─ README.md                # プロジェクト全体の顔
├─ docker-compose.yml       # PostgreSQL（後で frontend/backend も含めるか検討）
├─ docs/                    # 全体ドキュメント（journal, ADR, db-schema, api）
├─ backend/                 # Ktor + Exposed（今回の Phase 4.5 でここに移設）
│  └─ ...
└─ frontend/                # Next.js（Phase 6 完了後に追加）
   └─ ...
```

Phase 4.5 では、既存のプロジェクトルート直下にある Kotlin プロジェクトのファイル群（`src/`, `build.gradle`, `gradlew*`, `gradle/`, `settings.gradle`, `.idea/`）を `backend/` サブディレクトリに丸ごと移動する。`docs/` はプロジェクト全体のドキュメントとしてルート直下に据え置く。

## Consequences（この決定によって何が得られ、何を犠牲にしたか）

### 得られるもの

- **プロダクトとしての全体像が1つのリポジトリで完結する**: GitHub の1画面で、README・アーキテクチャ図・バックエンド・フロントエンドがすべて見える。他人（あるいは数ヶ月後の自分）がリポジトリを開いた時、全体像を把握するのが速い。
- **フロントとバックの整合性を1コミットで担保できる**: API 契約（DTO シェイプ）を変更するときに、backend と frontend の両方を同じ PR で修正できる。契約のズレによるバグを構造的に防ぎやすい。
- **ドキュメントを一元化できる**: 全体を跨ぐドキュメント（README、アーキテクチャ図、ADR）を `docs/` に集約でき、どちら側の変更もここで説明できる。
- **Docker Compose での一括起動が自然**: `docker compose up` で PostgreSQL・backend・frontend を同時に立ち上げるオーケストレーションが、リポジトリ構造とそのまま対応する。

### 犠牲にするもの

- **CI/CD の設計がやや複雑になる**: どのディレクトリの変更でどのジョブを走らせるか（path filtering）を設計する必要がある。ポリレポなら「そのリポジトリのファイル変更 = 全ジョブ実行」で済んでいた。
- **リポジトリのサイズが大きくなる**: Next.js の `node_modules` はビルド成果物として無視されるが、リポジトリ全体の clone が重くなる傾向はある（現時点の規模では問題にならない）。
- **IDE の得意領域が分かれる**: 今回は IntelliJ CE で backend、VS Code で frontend という**二刀流方針**（詳細は `~/.claude/plans/pc-springboot-kotlin-ktor-mac-giggly-key.md` の「IDE / 開発環境の運用指針」節、リポジトリ外ローカル参照）を採るため、この点は許容範囲。むしろポリレポでも二刀流は変わらないので実質的な差は薄い。

### 代替案として検討したもの

- **ポリレポ（`kotlin-todo-api` と `kotlin-todo-web` の2リポジトリ）**: 実務では「フロントとバックのデプロイ周期が違う組織」で採用されがち。今回は個人プロジェクトで「1プロダクトの全体像を1リポジトリでまとめて扱いたい」ため、モノレポを選択。
- **バックエンドをルートのまま維持、frontend/ だけを後で追加**: 既存のディレクトリを動かさずに済むが、「バックエンドがトップレベル、フロントエンドがサブディレクトリ」という非対称な構造になり、モノレポとしての整合性が損なわれる。前もって `backend/` に移す方針を採用。
- **Gradle Multi-project でバックエンドを分割**: バックエンド内部を複数モジュールに分けるのは意味があるが、それは Phase 5 以降のスケール要件が見えてから検討する。現時点では単一 Gradle プロジェクトのまま `backend/` に置く。

## 関連

- [ADR 0008 - Spring Boot から Ktor へ移行する](0008-migrate-from-spring-to-ktor.md)
- プラン file: `~/.claude/plans/pc-springboot-kotlin-ktor-mac-giggly-key.md`（ローカル参照）— Phase 4.5 のディレクトリ再編作業の詳細
- Phase 6 完了時に追加予定の ADR: フロントエンド着手時の DevContainer 構成、CORS 方針など
