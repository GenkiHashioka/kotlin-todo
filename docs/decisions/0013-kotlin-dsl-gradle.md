# 0013 - Gradle スクリプトを Groovy DSL から Kotlin DSL に切り替える

**ステータス**: 採用
**日付**: 2026-08-09

## Context（背景・何を解決したいか）

Phase 4 までのプロジェクトは、Spring Initializer が生成した初期状態のまま `backend/build.gradle` と `backend/settings.gradle` を Groovy DSL で持っていた。プロジェクト本体は Kotlin なのに、ビルドスクリプトだけが別言語（Groovy）で書かれている状態。

Phase 4.7 で Ktor 移行を進めるにあたり、`build.gradle` は依存も plugin も大幅に書き換わる。**同じタイミングで DSL も切り替えた方が変換コストが二重にならない**、という都合が背景。

また、Kotlin DSL への切り替えには以下の実用的メリットがあることも動機：

- **型付き**: `tasks.named<Test>("test")` のように型パラメータで対象タスクの型を指定でき、以降のブロック内で補完・型チェックが効く
- **IDE 補完**: IntelliJ が Kotlin DSL に対する補完を提供する（Groovy DSL の補完は動的言語ゆえに弱い）
- **Kotlin プロジェクトとしての一貫性**: 本体もビルドスクリプトも同じ言語なら、開発者は 1 言語だけ意識すればよい

## Decision（何を決めたか）

`backend/build.gradle` と `backend/settings.gradle` を **Kotlin DSL** に置き換える。具体的には：

- `settings.gradle` → `settings.gradle.kts`（Groovy → Kotlin syntax 変換、内容は 1 行）
- `build.gradle` → `build.gradle.kts`（同変換、Spring 依存や plugin 一覧はそのまま維持）
- `git mv` でリネームすることで、Git 履歴上「削除 + 新規追加」ではなく「rename」として追跡させる

**この PR では動作は一切変えない**。build 挙動・依存グラフ・テスト結果は Groovy DSL 時代と同一。純粋な syntax 変換にとどめ、次 PR での Ktor 移行と変更軸を分離する（[[feedback-per-phase-branch]] の思想に沿った小分け）。

### 変換で発生した書き方の変更点

Kotlin DSL の主な変換ルールを 5 点として記録：

1. **文字列リテラルはダブルクォート**: Kotlin は `'foo'` を `Char` 型（単一文字）専用に使うため、文字列は必ず `"..."`
2. **関数呼び出しに丸括弧が必要**: Groovy の「括弧省略」が Kotlin ではできないので `implementation("foo")` の形に
3. **Kotlin plugin は `kotlin("...")` ヘルパー**: `id("org.jetbrains.kotlin.jvm") version "..."` は `kotlin("jvm") version "..."` に短縮可
4. **タスクの型を明示**: `tasks.named<Test>("test") { ... }` のように型パラメータを付けると、ブロック内で補完・型チェックが効く
5. **`platform(...)` 依存の書き方**: `testImplementation(platform("..."))` のように括弧の入れ子

## Consequences（この決定によって何が得られ、何を犠牲にしたか）

### 得られるもの

- **IDE 補完・型チェックの恩恵**: 依存名 typo、タスクプロパティの typo などが編集時点で検出できる
- **Kotlin プロジェクトとしての一貫性**: 本体もビルドも Kotlin。学習者が Groovy syntax を別途覚えなくていい
- **Ktor 移行時に build.gradle.kts の書き方を「一度」学べば済む**: 次 PR で依存追加や plugin 差し替えが発生するが、DSL 自体は既に慣れた状態でその変更に臨める
- **Kotlin コミュニティの主流に合わせられる**: 最近の Kotlin プロジェクトのサンプル・ドキュメントは Kotlin DSL 前提のものが多く、そちらとの参照互換性が上がる

### 犠牲にするもの

- **初回 build がやや遅い可能性**: Kotlin DSL は build 時に Kotlin コンパイラで script をコンパイルする必要があり、Groovy より起動時のオーバーヘッドが微増する（実測では体感差なし、cache が効くと 2 回目以降は同等）
- **Groovy DSL 前提の古いドキュメント・記事との相互参照が必要**: Spring Boot のドキュメントや古い Stack Overflow の回答は Groovy DSL で書かれているケースが多く、Kotlin DSL に読み替える手間がある（主要な違いは上記 5 点なので慣れれば負担は軽い）
- **`tasks.named` の型指定など、Groovy より書く量が微増**: `tasks.named('test')` vs `tasks.named<Test>("test")` のように、型明示のぶん字数が増える箇所がある

### 代替案として検討したもの

- **Groovy DSL のまま維持**: 動くものを変えない、書く量が微減という利点はあるが、「Kotlin プロジェクトなのに build script だけ他言語」の不一致が残る。学習目的では Kotlin DSL に触れておく方が価値が高いと判断し却下
- **Ktor 移行と同一 PR で DSL 変換もまとめて実施**: 手数は減るが、「syntax 変換が原因の失敗」と「framework 差し替えが原因の失敗」の切り分けが困難になる。動作を変えない P1 と、大きく変える P2 に分けた方が debug 動線が単純

## 関連

- Phase 4.7 の P1 (Kotlin DSL 化) と P2 (Ktor 骨組み) の 2 PR 構成
- 次 PR で追加予定: ADR 0014（手動 DI over Koin）
- プラン file: `~/.claude/plans/pc-springboot-kotlin-ktor-mac-giggly-key.md` の Phase 4.7 節が一次ソース
