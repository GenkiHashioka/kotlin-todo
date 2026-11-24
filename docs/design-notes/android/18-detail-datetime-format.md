# 詳細設計メモ - Android 18: 詳細画面の作成日時・更新日時を読みやすい形で表示する

**バージョン**: 0.1
**作成日**: 2026-11-22
**対応Issue**: [#81](https://github.com/GenkiHashioka/kotlin-todo/issues/81)

## 1. 背景

Todo 詳細画面の「作成日時」「更新日時」に、API の値がそのまま出ている。

```
作成日時: 2026-11-16T00:24:49.721526
```

日付と時刻の間の `T` や、マイクロ秒（100 万分の 1 秒）まで出ていて読みにくい。#80 で README のスクリーンショット（`02-detail.png`）を撮っているときに本人が見つけた。

着手前に Claude が関連するコードを読んだ。

| 対象 | 現状 | 根拠 |
|---|---|---|
| Android の受け取り方 | `TodoDto` は `createdAt` / `updatedAt` を文字列（`String`）で受け取る | `TodoDto.kt`（Claude） |
| 詳細画面の表示 | `TodoDetailScreen` が `todo.createdAt` / `todo.updatedAt` を `作成日時: %1$s` / `更新日時: %1$s` にそのまま入れている | `TodoDetailScreen.kt`、`strings.xml`（Claude） |
| 詳細画面の状態 | `TodoDetailUiState.Success` は `TodoDto` をそのまま持つ。画面用の型（UI モデル）はない | `TodoDetailUiState.kt`（Claude） |
| API の返し方 | `TodoResponse` は `LocalDateTime`（タイムゾーンを持たない日時）を `toString()` で文字列にして返す | `TodoResponse.kt`、`LocalDateTimeSerializer.kt`（Claude） |
| 文字列の形 | `toString()` は秒以下が 0 の部分を省く。小数の桁数も値によって 3 桁・6 桁・9 桁と変わる。`2026-11-16T00:24` や `2026-11-16T00:24:49.721` の形で届くこともある | `LocalDateTime.toString()` の仕様（Claude） |
| 日時の記録 | バックエンドは `LocalDateTime.now()`（サーバーの時計とタイムゾーン設定のままの日時）で記録する | `TodoRepository.kt`（Claude） |
| 前例 | 入力の整え方は `ui/todo/TodoTitle.kt` に関数（`normalizeTodoTitle`）として切り出し、`TodoTitleTest` で単体テストしている | `TodoTitle.kt`、`TodoTitleTest.kt`（Claude） |
| 最低対応バージョン | `minSdk = 26`。日時を扱う標準ライブラリ（`java.time`）をそのまま使える | `build.gradle.kts`（Claude） |

関連文書: [Android 04: Todo詳細と画面遷移](04-todo-detail-navigation.md)、[Android 13: Todo画面の見やすさと操作性](13-todo-ui-usability.md)、[Android 16 のjournal: READMEのスクリーンショット](../../journal/android/16-readme-screenshots.md)。

## 2. スコープ

### この Issue で扱うもの

1. 詳細画面の作成日時・更新日時を `2026/11/16 00:24` の形で表示する
2. 変換する関数の単体テストを書く
3. README の `02-detail.png` を撮り直す

### 今回扱わないもの

- 一覧画面に日時を足すこと
- 期日（`dueDate`）の表示形式を変えること
- API が返す日時にタイムゾーンを付けること（3.5）
- 端末の言語設定に合わせて表示を変えること（3.1）

## 3. 設計方針

### 3.1 表示の形：`yyyy/MM/dd HH:mm`（秒なし）

本人と決めた（2026-11-22）。

| 案 | 判断 |
|---|---|
| `2026/11/16 00:24`（秒なし、24 時間表記） | 採用。Todo の作成・更新は分まで分かれば足りる |
| `2026/11/16 00:24:49`（秒あり） | 不採用。秒まで要る場面がない |
| 端末の言語設定に合わせた書式（`DateTimeFormatter.ofLocalizedDateTime`） | 不採用。端末によって見た目が変わり、テストで期待する文字列を固定しにくい。アプリの文言は今は日本語だけ |

秒以下は切り捨てる（`00:24:59` は `00:24`）。四捨五入はしない。書式に秒を入れなければ、何もしなくても切り捨てになる。

### 3.2 変換する場所：画面に出す直前に関数を呼ぶ

`ui/todo/detail/TodoDateTimeFormat.kt` に、API の文字列を受け取って表示用の文字列を返す関数（例：`formatTodoDateTime(value: String): String?`）を置く。`TodoDetailScreen` は `todo.createdAt` を直接入れずに、この関数を通した結果を入れる。読めなかったとき（`null`）の文言は画面側で決める（3.4）。

| 案 | 判断 |
|---|---|
| 関数に切り出し、画面に出す直前に呼ぶ | 採用。Android の画面部品なしで動くので、普通の単体テストで確かめられる。`TodoTitle.kt` と同じ形 |
| 画面用の型（UI モデル）を作り、ViewModel で変換してから状態に入れる | 不採用。今の詳細画面は `TodoDto` をそのまま持っている。日時 2 項目のためだけに型と変換処理を足すのは大きすぎる。画面用の型が他の理由で要るようになったら、そのとき変換もそちらへ移す |
| `TodoDto` で文字列ではなく `LocalDateTime` として受け取る（受け取り方の部品＝シリアライザを Android にも作る） | 不採用。受け取り時に形が合わないと、詳細画面だけでなく一覧の取得まで失敗になる（3.4）。表示を直す Issue で通信の型まで変えると影響が広い |
| `TodoDetailScreen` の中に変換処理を直接書く | 不採用。画面の部品の中だとテストしにくい |

置き場所は `ui/todo/` 直下ではなく `ui/todo/detail/` にする。今使うのは詳細画面だけである。`TodoTitle.kt` は登録・編集の両方が使うので `ui/todo/` 直下にある。一覧でも使うようになったら `ui/todo/` へ移す。

### 3.3 変換の仕方：日時の型に直してから書式を当てる

```kotlin
private val todoDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

fun formatTodoDateTime(value: String): String? =
    LocalDateTime.parse(value).format(todoDateTimeFormatter)
```

（形だけの例。失敗したときに `null` を返す処理は 3.4 で足す）

- `LocalDateTime.parse()` は ISO 8601（国際標準の日時の書き方）の文字列を日時の型に直す。秒の省略や小数の桁数の違い（1章の表）も受け付ける
- `DateTimeFormatter.ofPattern()` は「どう並べて文字にするか」の型紙を作る。型紙は毎回作らず、ファイルの中で 1 つだけ作って使い回す

| 案 | 判断 |
|---|---|
| `LocalDateTime.parse()` で型に直し、`DateTimeFormatter` で書式を当てる | 採用。日時として読むので、形が崩れた値に気づける（3.4）。3.5 でタイムゾーンを扱うときも、型に直す処理はそのまま土台になる |
| 文字列を切り出して置き換える（先頭 16 文字を取り、`-` を `/`、`T` を空白にする） | 不採用。今の形では動くが、日時として読んでいないので、壊れた値もそれらしい見た目で出てしまう。文字の位置に頼るので、API の形が変わると気づかず崩れる |

### 3.4 変換に失敗したとき：関数は `null` を返し、画面は「不明」と出す

`LocalDateTime.parse()` は、読めない文字列を受け取ると例外（`DateTimeParseException`）を投げる。画面で例外が起きるとアプリが落ちる。

本人と決めた（2026-11-22）。確かめていない文字列を利用者に見せる道を残さない。

- 関数は `DateTimeParseException` を受け止め、`null`（値がないことを表す特別な値）を返す
- 画面は `formatTodoDateTime(todo.createdAt) ?: unknownText` のように、`null` のとき「不明」を出す（`?:` は左が `null` なら右を使う書き方）
- 文言は `strings.xml` に `unknown`（`不明`）を足す。説明・カテゴリが空のときの `todo.description ?: unsetText`（「未設定」）と同じ形になる

| 案 | 判断 |
|---|---|
| 関数は `null` を返し、画面で「不明」と出す | 採用。読めない値を利用者に見せない。戻り値が `String?` なので、呼ぶ側が `null` の扱いを書かないとコンパイルが通らず、扱い忘れを型で防げる |
| 空文字（`""`）を返す | 不採用。画面に「作成日時: 」とラベルだけが残り、表示が壊れたのか値がないのか区別がつかない。失敗を普通の文字列で表すので、呼ぶ側が見落としやすい |
| 「未設定」（既存の `unset`）を流用する | 不採用。作成日時・更新日時はサーバーが必ず入れる値で、「設定されていない」とは意味が違う |
| 例外を受け止め、元の文字列をそのまま返す | 不採用。確かめていない文字列を利用者に見せる道が残る |
| 例外を受け止めず、そのまま投げる | 不採用。日時 1 項目の形が崩れただけで、アプリが落ちる |

API が今の形で返すかぎり、失敗は起きない想定である。画面からは何が届いたのか分からなくなるが、必要なときは API を直接呼んで確かめる。変換の関数の中でログ（`android.util.Log`）は出さない。出すと、Android の部品なしで動く普通の単体テストで動かなくなる。

### 3.5 タイムゾーン：今回は扱わない

API の日時はタイムゾーンを持たず、バックエンドはサーバーのタイムゾーン設定のまま記録している（1章の表）。サーバーと端末のタイムゾーンが違うと、表示がずれる。

今回は、届いた日時をそのまま表示する。サーバーも利用者も日本時間で、今は実害がない。直すには API の形（`OffsetDateTime` や `Instant` のようなタイムゾーン付きの型）、DB、バックエンドのテスト、Android の受け取り方まで変わり、表示を直すこの Issue の範囲を超える。

Backlog へ送る。着手条件は「サーバーを日本以外のタイムゾーンで動かすとき」か「次のプロダクトの要件で、日時を API に載せるとわかったとき」とする。

## 4. 実装ステップ

1. この設計メモをレビューする。
2. 単体テスト（`TodoDateTimeFormatTest`）を先に書き、失敗することを確かめる（5章のケース）。
3. `ui/todo/detail/TodoDateTimeFormat.kt` に変換の関数を書き、テストを通す。
4. `strings.xml` に `unknown`（`不明`）を足し、`TodoDetailScreen` の作成日時・更新日時で関数を呼ぶ。
5. エミュレーターで詳細画面を確かめ、README の `02-detail.png` を撮り直す。
6. journal を作成し、README、文書一覧、基本設計、要件、ロードマップ（Backlog への追加を含む）を必要に応じて更新する。
7. `/codex:review --wait --base main --cwd /mnt/c/Users/gensh/projects/kotlin-todo` を実行し、指摘の採否を決めてから、コミット・push・PR 作成へ進む。

各ステップでは、`LocalDateTime` と `DateTimeFormatter` の役割をコードと結び付けて説明し、完成コードを一括で提示しない。

## 5. 確認方法

以下は確認予定であり、実施済みではない。エミュレーターでの確認、Claude によるコード・テストレポートの確認、Codex のレビューを区別して journal へ記録する。

### 自動テスト

`TodoDateTimeFormatTest` で次を確かめる。

| ケース | 入力 | 期待する結果 |
|---|---|---|
| マイクロ秒まである（今の API の主な形） | `2026-11-16T00:24:49.721526` | `2026/11/16 00:24` |
| 小数が 3 桁 | `2026-11-16T00:24:49.721` | `2026/11/16 00:24` |
| 秒が省かれている | `2026-11-16T00:24` | `2026/11/16 00:24` |
| 秒が 59 でも切り上げない | `2026-11-16T23:59:59.999999` | `2026/11/16 23:59` |
| 1 桁の月・日・時が 0 埋めされる | `2026-01-05T09:03:00.1` | `2026/01/05 09:03` |
| 読めない文字列 | `invalid` | `null` |
| 空文字 | `""` | `null` |

`testDebugUnitTest` で、既存のテストも含めて成功する。

### 手動確認

Pixel 8 のエミュレーターで行う。

- 詳細画面の作成日時・更新日時が `yyyy/MM/dd HH:mm` の形で出る
- Todo を編集して保存した後、詳細画面の更新日時が新しい時刻になり、作成日時は変わらない
- 他の項目（タイトル、説明、期日、優先度、状態、カテゴリ）の表示が変わらない
- 「不明」の表示は、API が今の形で返すかぎりエミュレーターでは出せない。単体テストで `null` が返ることを確かめ、画面の `?:` の部分はコードで確かめる（手動確認はしない）
- 撮り直した `02-detail.png` で日時が新しい形になっている

## 6. 想定される詰まりポイント

- **import の取り違え**：`LocalDateTime` と `DateTimeFormatter` は `java.time` / `java.time.format` から import する。`kotlinx.datetime` の同じ名前の型と取り違えない（このプロジェクトでは `kotlinx.datetime` を使っていない）
- **書式の大文字・小文字**：`MM` は月、`mm` は分、`HH` は 24 時間表記の時、`hh` は 12 時間表記の時。取り違えると、テストの 0 埋めのケースや午後の時刻で気づく
- **受け止める例外の範囲**：`catch (e: Exception)` のように広く受け止めると、別の原因の不具合まで隠れる。受け止めるのは `DateTimeParseException` だけにする
- **画面を組み立て直すたびに変換が走る**：Compose は状態が変わるたびに画面の関数を呼び直す。変換は軽い処理なので、結果を覚えておく仕組み（`remember`）は使わない。型紙（`DateTimeFormatter`）を毎回作らないことだけ守る（3.3）
