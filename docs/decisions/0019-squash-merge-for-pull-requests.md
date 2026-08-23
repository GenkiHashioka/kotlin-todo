# 0019 - Pull Request のマージは squash に統一する

**ステータス**: 採用
**日付**: 2026-08-23

## Context（背景・何を解決したいか）

このリポジトリの `main` には、**2 つのマージ方式が混在している**。

```
a88ab15 Testcontainers を Docker Engine 29 に対応させ、テストを復旧する (#33)   ← squash
42fcd9a Phase 4.9 (c): Konform バリデーション + StatusPages 拡張 (#30)          ← squash
62a63af chore: normalize line endings to LF (#27)                              ← squash
a8032a9 Merge pull request #24 from GenkiHashioka/phase-04.9b-ktor-routing      ← merge commit
```

内訳は **merge commit 19 件（#1〜#24）、squash 3 件（#27 / #30 / #33）**。

切り替わったのは #27 で、この PR は「改行コードを LF に統一する」もの。28 ファイル / +1224 −1219 の中身がほぼ全部機械的な正規化だったため、`main` の履歴にノイズを並べたくないという理由で squash を使った。**その 1 回限りの判断が #30 以降も踏襲された**というのが実態で、方針として決めた記録は残っていない。

GitHub 側の設定も揃っていない。

| 設定 | 現状 |
|---|---|
| `allow_merge_commit` | `true` |
| `allow_squash_merge` | `true` |
| `allow_rebase_merge` | `true` |
| `delete_branch_on_merge` | `false` |

3 方式すべてが許可されたままで、マージ後のリモートブランチも自動削除されない。**判断も設定も宙に浮いている**ため、ここで decide する。

## Decision（何を決めたか）

### 1. マージは squash に統一する

```bash
gh pr merge <番号> --squash --delete-branch
```

`main` は **1 PR = 1 コミットの一直線**とする。

#### 3 方式の比較

| | merge commit | **squash** | rebase merge |
|---|---|---|---|
| `main` の形 | 分岐が残る二次元 | **一直線** | 一直線 |
| `main` に入るコミット数 | PR 内の全コミット + マージコミット | **1** | PR 内の全コミット |
| PR 内のコミット分割 | 残る | **本文に文章として残る** | コミットとして残る |
| PR 全体の revert | `git revert -m 1 <merge>` | **`git revert <commit>`** | 複数コミットを個別に revert |
| PR 内の一部だけ revert | 可能 | **不可** | 可能 |
| `git log --oneline` の読みやすさ | 作業単位のコミットが全部並ぶ | **PR 単位で 1 行** | 作業単位のコミットが全部並ぶ |
| `git bisect` | 中間コミットを踏む（`--first-parent` で回避可） | **全コミットが完成状態** | 中間コミットを踏む |
| 各コミットの独立性の要求 | 低い | **不要** | **高い**（全コミットが単独でビルドできる必要） |
| ブランチ側の元コミット | `main` に含まれる | **失われる**（ブランチ削除後は GC 対象） | 別 SHA で `main` に入る |

### 2. なぜこのプロジェクトでは squash が合うか

**PR 内のコミットが「作業手順」であって、独立した意味を持たないため。**

このプロジェクトの PR は次のような並びになる。

```
docs: add design-note for phase 4.9 (c)      ← 設計だけ。コードは無い
feat: add Konform validation and ...          ← 実装
docs: add phase 4.9 (c) journal and ADR ...   ← 記録
```

これを merge commit や rebase merge で入れると、**「設計メモだけあってコードが無い」コミットが `main` に単独で並ぶ**。その時点の `main` をチェックアウトしても、design-note が説明している実装は存在しない。履歴上の各点が「意味のある状態」になっていない。

squash すると `main` の各コミットが常に「設計 + 実装 + ドキュメントが揃った完成状態」になる。`git bisect` で任意の地点に飛んでもビルドできる。

**rebase merge を採らない理由も同じ**。rebase merge は各コミットが単独で成立していることを前提にした方式で、上記の並びとは相性が悪い。

### 3. PR 内のコミットは引き続き「変更の理由」で分ける

squash で 1 つに潰れるが、**分ける意味は残る**。GitHub の `squash_merge_commit_message` が `COMMIT_MESSAGES` に設定されているため、各コミットの本文が箇条書きで連結されて `main` に残るからである。

実例（#33 のマージ結果 `a88ab15`）:

```
Testcontainers を Docker Engine 29 に対応させ、テストを復旧する (#33)

* chore: update Gradle wrapper to 9.7.0

  Gradle 9.5.1 から 9.7.0 へ更新。gradlew / gradlew.bat の差分は
  コメント文字列の書き換えのみで、実行ロジックの変更は無い。
  （以下略）

* fix: pin Docker API version used by Testcontainers
  （以下略）
```

分ける理由は 3 つ。

- **`main` の本文に理由が残る**（上記のとおり）
- **レビューが段階的に読める**：PR の Commits タブで 1 つずつ差分を追える
- **書く側の思考が整理される**：「これは同じ理由の変更か」を問う作業そのものが設計の検算になる

### 4. PR タイトルは `main` のコミット件名になる

`squash_merge_commit_title` が `COMMIT_OR_PR_TITLE` なので、**PR タイトルがそのまま `main` の 1 行目**になる。したがって PR タイトルは「作業名」ではなく「その PR が何を達成したか」を書く。

```
○ Testcontainers を Docker Engine 29 に対応させ、テストを復旧する
× #25 対応
```

### 5. GitHub 側の設定も揃える

判断をコマンドの打ち方だけに委ねず、リポジトリ設定で強制する。

| 設定 | 変更後 | 理由 |
|---|---|---|
| `allow_squash_merge` | `true` | 採用する方式 |
| `allow_merge_commit` | `false` | 誤って別方式を選べないようにする |
| `allow_rebase_merge` | `false` | 同上 |
| `delete_branch_on_merge` | `true` | マージ後にリモートブランチを自動削除する |

**`delete_branch_on_merge` はリモートブランチしか消さない。** ローカルブランチは `origin/xxx: gone` の状態で残るため、`gh pr merge` には引き続き `--delete-branch` を付ける（このフラグはローカルとリモートの両方を消す）。設定はあくまで、UI からマージした場合やフラグを付け忘れた場合の保険である。

**残ったローカルブランチは `git branch -d` では消せない。** squash はブランチのコミットを取り込むのではなく、同じ内容の**別のコミット**を `main` に作る操作なので、ブランチ先端は `main` の祖先にならない。`-d` は中身ではなく祖先関係を見るため、squash 運用では全ブランチが「未マージ」と判定される。

```
$ git branch -d docs/adr-0019-squash-merge
error: the branch 'docs/adr-0019-squash-merge' is not fully merged

$ git rev-parse docs/adr-0019-squash-merge^{tree}   # ブランチのツリー
146b3f7fd482c7cd6c86ce18fb223190fdb0422a
$ git rev-parse main^{tree}                          # main のツリー
146b3f7fd482c7cd6c86ce18fb223190fdb0422a             # 完全に同一
```

したがって手で消す場合は `-D` を使う。ただし `-d` が持っていた安全性（未マージなら止める）が失われるため、**`gh pr merge --squash --delete-branch` を常用する**。`gh` は PR がマージ済みであることを GitHub API で確認してから削除するので、git の祖先判定に頼らずに済む。

### 6. 既存の merge commit は書き換えない

#1〜#24 の 19 件はそのまま残す。履歴の書き換えは、得られるもの（見た目の統一）に対して失うもの（全 SHA の変化、既存の参照リンク切れ）が大きすぎる。**#27 以降が squash である**という事実を本 ADR が説明できていれば、混在していても読める。

## Consequences（この決定によって何が得られ、何を犠牲にしたか）

### 得られるもの

- **`main` が「完成状態の列」になる**：任意のコミットをチェックアウトしてビルドできる。`git bisect` に `--first-parent` のような但し書きが要らない
- **`git log --oneline` が PR の一覧になる**：1 行 = 1 PR = 1 まとまった変更。フェーズ単位で進んできた本プロジェクトの実態と一致する
- **revert が単純**：`git revert <commit>` で済む。merge commit の `-m 1`（どちらの親を本流とみなすか）を考えなくてよい
- **設定で強制される**：方式を毎回選ばずに済み、打ち間違いも起きない
- **ブランチ名が履歴に残らない**：`phase-04.9b-ktor-routing` のような作業用の名前が `main` の件名を占有しなくなる

### 犠牲にするもの

- **PR 内の一部だけを revert できない**：#33 で言えば「Gradle 更新だけ戻す」ができない。手で逆パッチを当てることになる。**PR のスコープを小さく保つ**ことでしか緩和できず、これは squash を選ぶ以上そのまま受け入れる代償
- **コミット単位のファイル対応が失われる**：`git log --name-only` で「どのコミットがどのファイルを触ったか」を追えない。本文は残るが、対応関係は残らない
- **ブランチ側のコミットが消える**：`--delete-branch` するとブランチが消え、元の 4 コミットは参照されなくなり最終的に GC される。PR ページには残るが、ローカルの `git` からは辿れなくなる
- **履歴が混在したままになる**：#1〜#24 と #27 以降で形が違う。本 ADR がその説明になる
- **`main` のコミットが大きくなる**：#33 は 11 ファイル / +140 −13 が 1 コミット。将来 PR が大きくなると、1 コミットの粒度が粗くなりすぎる可能性がある

### 代替案として検討したもの

- **merge commit（#24 までの方式）に戻す**：PR 内のコミットが全部 `main` に残るため、一部だけの revert や `git log --name-only` での追跡ができる。分岐の形も見える。ただし「design-note だけのコミット」が `main` に単独で並ぶ問題が解決しない。またソロ開発では分岐の形に情報量がほとんど無い（並行開発をしていないため、分岐は常に `main` から生えて `main` に戻るだけ）。却下
- **rebase merge**：`main` が一直線でコミットも全部残るという、両方の良いところ取りに見える。しかし**各コミットが単独でビルドできることが前提**の方式で、「設計 → 実装 → 記録」という並びには合わない。また `main` のコミット数が PR のコミット数だけ増え、`git log --oneline` が作業ログになる。却下
- **PR の性質で使い分ける**（機能追加は merge、雑務は squash など）：判断が毎回必要になり、結局 #27 で起きたこと（その場の判断が定着する）が繰り返される。ソロ開発で規約を増やす利得が薄い。却下
- **squash に統一しつつ設定は 3 方式とも許可のまま**：現状維持。運用は変わらないが、打ち間違いを防げず、この ADR を読んでいない将来の自分が別方式を選べてしまう。却下

## 関連

- [ADR 0013 - Gradle スクリプトを Groovy DSL から Kotlin DSL に切り替える](0013-kotlin-dsl-gradle.md) — 同じく開発フローに関する判断
- `docs/README.md` — ドキュメント運用方針
- PR [#27](https://github.com/GenkiHashioka/kotlin-todo/pull/27) — squash を初めて使った PR
- PR [#33](https://github.com/GenkiHashioka/kotlin-todo/pull/33) — コミットを 4 分割した実例。squash 後の本文は `a88ab15` で確認できる
