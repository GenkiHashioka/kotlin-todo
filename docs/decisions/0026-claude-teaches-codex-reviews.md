# 0026 - 個人開発ではClaude Codeを先生役、Codexをレビュー役に分ける

**ステータス**: 採用
**日付**: 2026-10-28

## Context（背景・何を解決したいか）

このリポジトリでは、2026-09-20 の PR [#40](https://github.com/GenkiHashioka/kotlin-todo/pull/40) で `AGENTS.md` を追加し、Codex に先生役（mentor）とレビュー役を兼ねてもらっていた。Claude Code 向けの `CLAUDE.md` も並行して存在し、どちらの AI も「説明・下書き・レビュー」を担える状態だった。

その後、公式プラグイン `codex-plugin-cc` を導入し、Claude Code の中から Codex を呼べるようになった。プラグインが提供する主なコマンドは次のとおり。

| コマンド | 役割 |
|---|---|
| `/codex:review` | 現在の差分を Codex にレビューさせる |
| `/codex:adversarial-review` | 実装方針・設計判断・前提そのものを疑うレビュー |
| `/codex:rescue` | 調査や修正を Codex に任せる（Codex がコードを書く） |
| `/codex:setup --enable-review-gate` | Claude の応答が終わるたびに Codex レビューを自動実行する |

`/codex:review` と `/codex:adversarial-review` は、Claude からは起動できず、開発者が自分で実行する設定になっている。

役割を決めずに 2 つの AI を使うと、次の問題が起きる。

- 同じ説明やレビューを 2 回受けることになり、時間と利用枠を消費する
- 説明した AI 自身がレビューすると、説明時と同じ見落としをしやすい
- 両方が先生役になると、指針が食い違ったときにどちらを基準にするかが曖昧になる

このプロジェクトは学習が目的であり、実装コードは開発者本人が書く（`CLAUDE.md` の「役割分担」）。AI の役割分担も、この前提に合わせて決める。

## Decision（何を決めたか）

### 1. Claude Code を先生役にする

概念・責務・選択肢の説明、design-note / journal の下書き、Codex の指摘の解説、開発者の説明の抜けの指摘を担う。

### 2. Codex をレビュー役にする

開発者が実装後に `/codex:review --base main` を実行する。設計判断を含む PR では `/codex:adversarial-review --base main <観点>` も実行する。全 PR で両方は実行しない。

### 3. Codex に実装させる機能は使わない

`/codex:rescue` は使わない。review gate は無効のままにする。

### 4. 指摘の採否は開発者が決める

2 つの AI の指摘が食い違ったときは、どちらかに寄せず、理由を並べて開発者が判断する。Codex が使えないとき（利用上限など）は、Claude Code の `/code-review` で代替する。

### 5. ルールは両方の指示ファイルに書く

Claude 側のルールは `CLAUDE.md`、Codex 側のルールは `AGENTS.md` の「AI Usage」に書く。各 AI が自動で読むファイルに置くため。開発者がプラグインを通さず Codex と直接会話する場合は、`AGENTS.md` の mentor 役の記述をそのまま適用する。

## Consequences（この決定によって何が得られ、何を犠牲にしたか）

### 得られるもの

- **説明とレビューが別のモデルになる**：説明した側と同じ見落としを、別の視点で拾える
- **説明の基準が 1 つになる**：先生役は Claude Code だけなので、指針の食い違いが起きにくい
- **レビューのタイミングを開発者が決める**：レビューコマンドは開発者しか起動できないため、「AI が勝手に進める」状態にならない
- **実装を代行する経路を閉じる**：「本人が書く」方針と矛盾する機能を使わないことが明文化される

### 犠牲にするもの

- **Codex の先生としての説明を日常的には受けなくなる**：PR #40 以降に Codex から受けていた指導の形が変わる
- **レビュー漏れの可能性**：`/codex:adversarial-review` を設計判断を含む PR に限るため、どの PR が該当するかの判断を誤ると深いレビューを受けずに進む
- **開発者の手順が増える**：レビューのたびにコマンドを自分で実行する必要がある
- **ルールが 2 ファイルに分かれる**：`CLAUDE.md` と `AGENTS.md` の両方を更新しないと食い違う

### 代替案として検討したもの

- **Codex を先生役、Claude Code をレビュー役にする（役割の入れ替え）**：プラグインは Claude Code の中から Codex を呼ぶ構成で、Codex 側のコマンドはレビューのように 1 回ごとに完結する使い方を想定している。会話を続けながら教わる先生役は、会話の主体である Claude Code の方が合う。却下
- **1 つの AI に説明とレビューの両方を任せる**：説明した側がレビューすると同じ見落としをしやすく、別のモデルを入れる意味が無くなる。却下
- **全 PR で `/codex:review` と `/codex:adversarial-review` の両方を実行する**：小さな PR では指摘が重なりやすく、利用枠も早く尽きる。却下
- **review gate を有効にする**：このリポジトリでは Claude がコードを書かないため、応答ごとのレビューは対象がほぼ無く、利用枠だけを消費する。却下
- **ルールをユーザー全体の設定（`~/.claude/CLAUDE.md`・`~/.codex/AGENTS.md`）に置く**：仕事のリポジトリにも適用されてしまい、Git で管理されないため PR でレビューできない。却下

## 関連

- [ADR 0019 - Pull Request のマージは squash に統一する](0019-squash-merge-for-pull-requests.md) — 同じく開発フローに関する判断
- `CLAUDE.md` の「AI の役割分担」
- `AGENTS.md` の「AI Usage」
- PR [#40](https://github.com/GenkiHashioka/kotlin-todo/pull/40) — Codex 向けの `AGENTS.md` を追加した PR
