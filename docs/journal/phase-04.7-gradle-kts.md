# Phase 4.7 (P1) — Gradle スクリプトを Kotlin DSL に変換

**ステータス**: P1 完了。P2（Ktor 骨組み + Spring 撤去）に続く
**開始日**: 2026-08-09
**完了日**: 2026-08-09

## 学習目標

- Groovy DSL と Kotlin DSL の違い（構文レベルの変換ルール）
- `settings.gradle` / `build.gradle` から `.kts` 版への変換手順
- Kotlin plugin のヘルパー関数 `kotlin("...")` と汎用の `id("...")` の使い分け
- Kotlin バージョンと Java バージョンの位置付けの違い

## 成果物

- `backend/settings.gradle.kts`（`settings.gradle` からリネーム + syntax 変換）
- `backend/build.gradle.kts`（`build.gradle` からリネーム + syntax 変換、依存や plugin は同じ）
- `docs/decisions/0013-kotlin-dsl-gradle.md`（新規、Kotlin DSL 採用の判断記録）
- `docs/decisions/README.md`（0013 を index に追加）

## チェックポイント結果

- `./gradlew build` グリーン
- `./gradlew test` グリーン（Phase 4.6 完了時と同じ 4 テストが引き続き green）
- 動作・挙動が Phase 4.6 完了時と完全に同一であることを確認（動作を変えない syntax 変換のみ、が方針通り成立）

## 学んだこと

### Groovy DSL vs Kotlin DSL の主な違い（5 点）

`build.gradle` → `build.gradle.kts` の変換で発生した変更ルール。次にプロジェクトで同種の変換をやる時の参照として。

1. **文字列リテラルは必ずダブルクォート** `"..."` — Kotlin は `'...'` を単一文字 (`Char` 型) 専用に使う
2. **関数呼び出しに丸括弧が必要** — Groovy は `implementation 'foo'` のように括弧省略できるが、Kotlin は `implementation("foo")` が必須
3. **Kotlin plugin は `kotlin("...")` ヘルパーで短縮できる** — `id("org.jetbrains.kotlin.jvm") version "..."` → `kotlin("jvm") version "..."`
4. **タスクの型を明示すると補完が効く** — `tasks.named<Test>("test") { ... }` の `<Test>` があると、ブロック内で `useJUnitPlatform()` などの補完が働く
5. **`platform(...)` は括弧の入れ子** — `testImplementation(platform("org.testcontainers:testcontainers-bom:1.20.4"))`

### `git mv` でリネーム履歴を残す

Git は「ファイル名 A が削除、B が新規追加」という 2 つの操作と「A → B へのリネーム」を区別できる。`git mv A B` を使うと後者として記録され、`git log --follow` で「A 時代の履歴も追える」形になる。ただの `mv A B` だと Git は「削除 + 新規」と誤認して履歴が切れる。

今回 `build.gradle` → `build.gradle.kts` は「同じファイルを Kotlin syntax にした」意味なので、履歴を切らない `git mv` が適切。

### Kotlin バージョンと Java バージョンの位置付けの違い

作業中に「なぜ Kotlin のバージョン選定は気にしなかったのか」という疑問が浮かんだ。整理すると：

- **Kotlin バージョン = コンパイラの版数**。新しくすると「バグ修正 / 最適化 / 新機能」が入るが、既存 Kotlin コードはほぼ動き続ける。Kotlin は互換性を重視する言語で、メジャー版内では基本的にソース互換
- **Java バージョン = JVM（実行環境）そのものの版数**。GC / 実行時挙動 / 使える言語機能 / 使えるライブラリ、が全部変わる可能性がある。だから慎重な判断が必要
- **Spring Boot BOM が Kotlin バージョンの推奨を提供**。Spring Initializer で作った時点で「Boot 4.1 + Kotlin 2.3.21」が組み合わせとして選ばれていて、それが公式にテスト済み。触る理由がなかった

つまり Kotlin バージョンを「気にしなくてよかった」のは互換性が高いから + Boot BOM が推奨を提供してくれてるから、の 2 段の理由。

### 動作を変えない PR の価値

今回の P1 は「syntax 変換だけ、動作は完全同一」を意図した。この方針の副産物：

- **build/test green が「変換が正確」の証明** になる。Groovy 版と挙動が違えば syntax 変換にミスがある証拠
- 次 PR (P2, Ktor 骨組み) で失敗した時、「syntax の疑い」を除外できる。debug が単純化する
- 変更軸を「syntax 変換 (P1)」と「framework 差し替え (P2)」に分離できる

## 実運用に関わる既知の課題（今回は対応を見送り）

- **なし**: 純粋な syntax 変換で、機能追加も削除もしていない。P2 で Ktor 骨組みに進む
