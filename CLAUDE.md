# CLAUDE.md

Kotlin 学習を目的とした Todo アプリのモノレポ。バックエンドは Ktor + Exposed + PostgreSQL。

このファイルは Claude Code 向けの運用ルール。**なぜそう決めたか**は書かない。判断の背景は [`docs/decisions/`](docs/decisions/README.md)（ADR）を参照。

## 役割分担

- **実装コードは開発者本人が書く。** Claude は下書きを提示し、書き方を解説する。本人が書いた実装をレビューするのは可
- **ドキュメント（ADR / journal / design-note / README）は Claude が下書きし、本人がレビューする**
- **git / gh の書き込み操作（commit / push / merge / issue 起票 / PR 作成）は全て本人が実行する。** Claude はコマンドを提示し、フラグの意味を説明する。読み取り系（`git status` / `git log` / `gh pr view` など）は Claude が実行してよい

## 開発環境

Windows 11 + WSL2 (Ubuntu)。Claude Code と Codex は WSL でだけ起動する。クローンは 2 つある。
- WSL 側 `~/projects/kotlin-todo`：backend とドキュメント。git / gradle はそのまま実行する。gradle の前に `source ~/.sdkman/bin/sdkman-init.sh` が要る
- Windows 側 `/mnt/c/Users/gensh/projects/kotlin-todo`（Windows では `C:\Users\gensh\projects\kotlin-todo`）：android。Claude は WSL からこのパスでファイルを読み書きする。Android のビルド・テストは開発者が Windows 側で `android\gradlew.bat` を使って実行し、Claude は `android/app/build/` の結果やレポートを読んで確認する
- Windows 側クローンは `core.fileMode=false` を前提とする
- 作業前に、どちらのクローン・ブランチで作業しているかを確認する

## コマンド

```bash
docker compose up -d postgres   # 開発用 DB 起動（リポジトリルート）

cd backend
./gradlew run                   # アプリ起動（http://localhost:8080）
./gradlew build                 # ビルド + テスト
./gradlew test                  # テストのみ
./gradlew compileKotlin         # コンパイルのみ
```

## Git / PR 運用

- **Phase / サブタスクごとにブランチを切り、PR 経由でマージする。** ソロ開発でも直接 `main` にコミットしない
- **マージは squash に統一する**（[ADR 0019](docs/decisions/0019-squash-merge-for-pull-requests.md)）

  ```bash
  gh pr merge <番号> --squash --delete-branch
  ```

  `--delete-branch` は省略しない。リポジトリ設定の自動削除はリモート側にしか効かず、squash 後のローカルブランチは `git branch -d` では消せない
- **PR のマージ後は必ず `git status` で漏れた変更が無いか確認する**
- **複数行のテキスト（コミットメッセージ / PR body / issue body）は必ず file 経由で渡す。** この端末は複数行の貼り付けが崩れる

  ```bash
  git commit -F ~/commit-msg.txt
  gh pr create --body-file ~/pr-body.md
  gh issue create --body-file ~/issue.md
  ```

  下書きファイルは使い終わったら削除する
- **PR 内のコミットは「変更の理由が同じか」で分ける。** squash されても各コミットの本文は `main` に残る
- **Claude Code のセッションは 1 Issue につき 1 つ。** 話題が Issue の範囲を外れたら、決定事項を design-note / journal に残したうえで `/clear` を勧める

## コーディング

- **変数名は省略しない。** `categoryRepository` と書く。`categoryRepo` のような短縮形は使わない
- 層の責務とパッケージ構成は [`docs/architecture.md`](docs/architecture.md) に従う

## 説明の仕方

- **専門用語は初出時に必ず平易な言い換えを添える**
- **1 メッセージにつき論点は 1 つ**にする
- **推奨案は 1 つに絞る。ただし何と比較してそう決めたかを必ず添える。** 「A / B / C のどれにしますか」と判断を丸投げしない一方、推奨案だけを提示して代替案を伏せることもしない。「A を推す。B は〜という理由で却下、C は〜」の形にする。ADR の「却下した代替案」と同じ構造

## ドキュメント更新の手順

設計メモの着手前作成、実装後のjournal、PR前の進捗文書更新は[AGENTS.mdの「Documentation Workflow」](AGENTS.md#documentation-workflow)を共通ルールとして適用する。下書きは対象ブランチの作業リポジトリへ直接保存し、開発者がレビューする。

## ドキュメント構成

| | 役割 |
|---|---|
| [`README.md`](README.md) | プロジェクトの入口 |
| [`docs/requirements.md`](docs/requirements.md) | 何を作るか |
| [`docs/architecture.md`](docs/architecture.md) | どう作るか（全体構成） |
| [`docs/design-notes/`](docs/design-notes/README.md) | 実装前の詳細設計メモ（書き捨て） |
| [`docs/test-plans/`](docs/test-plans/README.md) | テスト観点・前提条件・手順・期待結果 |
| [`docs/testing/`](docs/testing/README.md) | 実施環境・実際の結果・証拠・判定 |
| [`docs/journal/`](docs/journal/) | フェーズごとの学習記録 |
| [`docs/decisions/`](docs/decisions/README.md) | 設計判断の記録（ADR） |
