# 詳細設計メモ - Android 17: 画面を進む操作の連打で同じ画面が2重に開かないようにする

**バージョン**: 0.1
**作成日**: 2026-11-21
**対応Issue**: [#78](https://github.com/GenkiHashioka/kotlin-todo/issues/78)

## 1. 背景

#74 の確認中に、画面を進む操作を素早く2回押すと、同じ画面が2重に開くことが分かった。戻るときに、同じ画面をもう一度戻る必要がある。

- Todo一覧の行を選ぶ → 詳細画面が2つ開く
- Todo一覧の「Todoを作成」ボタン → 登録画面が2つ開く
- Todo詳細の「編集」ボタン → 編集画面が2つ開く

#74 では、戻る操作の連打で前の画面を越えて戻る問題を、画面が操作を受け付けている状態（`RESUMED`）のときだけ処理を通す仕組み（`dropUnlessResumed`）で直した（[Android 15 のjournal](../../journal/android/15-form-back-button.md)）。画面を移る処理（`navigate()`）も、呼ばれた回数だけ画面を積む。画面の切り替えアニメーションの間の2回目のタップで、もう1つ画面が開くと考えられる。

着手前に Claude が関連するコードを読んだ。エミュレーターではまだ確かめていない（現象は #74 の確認中に本人が見つけたもの）。

| 対象 | 現状 | 根拠 |
|---|---|---|
| 一覧の行の選択 | `KotlinTodoNavHost` が `onTodoClick = { todoId -> navController.navigate(TodoDetailDestination(todoId = todoId)) }` を渡している。どの Todo かを表す引数（`todoId`）を受け取る | `KotlinTodoNavHost.kt`（Claude） |
| 「Todoを作成」ボタン | `onCreateClick = { navController.navigate(TodoCreateDestination) }`。引数はない | `KotlinTodoNavHost.kt`（Claude） |
| 詳細の「編集」ボタン | `onEditClick = { todoId -> navController.navigate(TodoEditDestination(todoId = todoId)) }`。`todoId` を受け取る | `KotlinTodoNavHost.kt`（Claude） |
| 戻る操作 | 3画面の `onBack` を `dropUnlessResumed { navController.popBackStack() }` で包んでいる（#74） | `KotlinTodoNavHost.kt`（Claude） |
| 画面側 | 画面は `onTodoClick(todo.id)` / `onCreateClick` / `onEditClick(todo.id)` を呼ぶだけで、画面遷移の仕組み（`NavController`）を知らない | `TodoListScreen.kt`、`TodoDetailScreen.kt`（Claude） |
| 使っているライブラリ | `lifecycle-runtime-compose` 2.11.0、`navigation-compose` 2.10.1 | `libs.versions.toml`（Claude） |

関連文書: [Android 15: 登録・編集画面に画面上の戻るボタンを追加する](15-form-back-button.md)、[Android 04: Todo詳細と画面遷移](04-todo-detail-navigation.md)。

## 2. スコープ

### この Issue で扱うもの

1. 一覧の行の選択、「Todoを作成」ボタン、詳細の「編集」ボタンを連打しても、画面が1つだけ開くようにする
2. 他に連打で2重に動く操作がないかを確かめる（3.3）

1回押したときは今までどおり進める。#74 の戻る操作の連打対策、成功後の Snackbar と再取得を維持する。

### 今回扱わないもの

- 画面遷移の構成（`KotlinTodoNavHost` の画面の並び）の変更
- 全画面のデザイン変更、タイトルバー（`TopAppBar`）の導入
- 再試行ボタンの連打（3.3 で扱いを書く）
- 連打を自動テストで確かめる UI テストの追加（5章）

## 3. 設計方針

### 3.1 止める場所：`KotlinTodoNavHost` で止める

#74 と同じく、画面遷移を扱う `KotlinTodoNavHost` で止める。画面のコードは変えない。

| 案 | 判断 |
|---|---|
| `KotlinTodoNavHost` で、`navigate()` の前に画面の状態を確かめる | 採用。連打は画面遷移の問題なので、遷移を扱う場所で止める。#74 の `onBack` と同じ場所にそろう |
| 画面側で「押したか」のフラグを持つ（`remember { mutableStateOf(false) }`） | 不採用。#74 と同じ理由。一覧の行・「Todoを作成」・「編集」のそれぞれに仕組みが要り、戻ってきたときにフラグを戻す処理も考える必要がある |
| `navigate()` に、同じ画面が一番上にあれば積まない指定（`launchSingleTop = true`）を付ける | 不採用。違う行を素早く続けて選ぶと、別の Todo の詳細として2つ開く可能性がある。引数が違う画面を「同じ画面」とみなすかの細かい動きも確かめていない（要確認）。「遷移中のタップを捨てる」という #74 の考え方とも違う |

### 3.2 止め方：引数がない操作は `dropUnlessResumed`、引数がある操作は同じ判定を書く

`dropUnlessResumed` は引数のない処理（`() -> Unit`）しか包めない。一覧の行と「編集」は `todoId` を受け取る処理（`(Long) -> Unit`）なので、そのままでは包めない。

`dropUnlessResumed` の中身は「今の画面のライフサイクルが `RESUMED` 以上なら処理を通す」という判定である。`NavHost` の中では、今の画面のライフサイクルはその画面の `backStackEntry`（画面の積み重ねの1枚分の情報）が持っている。そこで、引数がある2か所は、同じ判定を `backStackEntry` を使って書く。

```kotlin
// 引数がない：#74 の onBack と同じ形
onCreateClick = dropUnlessResumed {
    navController.navigate(TodoCreateDestination)
},

// 引数がある：dropUnlessResumed と同じ判定を書く
onTodoClick = { todoId ->
    if (backStackEntry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
        navController.navigate(TodoDetailDestination(todoId = todoId))
    }
},
```

詳細画面の「編集」も、一覧の行と同じ形にする。一覧・詳細の `composable` は、再取得の通知を受け取るために `backStackEntry` を既に受け取っているので、それを使う。

| 案 | 判断 |
|---|---|
| 引数がない操作は `dropUnlessResumed`、引数がある操作は `backStackEntry` で同じ判定を書く | 採用。どちらも「その画面が `RESUMED` のときだけ通す」という同じ判定になる。ライブラリや新しい関数を足さない |
| 3つとも `backStackEntry` の判定を手書きする | 不採用。動きは同じだが、`onBack`・`onCreateClick` のように `dropUnlessResumed` で包める所まで手書きにすると、#74 で「手書きは `dropUnlessResumed` と同じことをするだけ」と不採用にした判断とずれる |
| 画面側で `dropUnlessResumed { onTodoClick(todo.id) }` のように、行ごとに包む | 不採用。連打対策が画面側へ散らばり、3.1 の「遷移を扱う場所で止める」から外れる。`dropUnlessResumed` は Composable（画面を組み立てる関数）なので、一覧の行ごとに呼ぶことにもなる |
| 引数付きの処理を包む関数を自分で作る | 不採用。使うのは2か所だけで、関数を作るほどの重複ではない。増えた時点で検討する |

`navController.currentBackStackEntry` ではなく、その画面の `backStackEntry` を使う。`currentBackStackEntry` は「今一番上にある画面」を指すため、1回目のタップで遷移が始まった後は、開こうとしている新しい画面を指してしまう。#74 の journal で不採用とした「`navController.currentBackStackEntry?.lifecycle?.currentState` で判定する」案は、この点でも今回の案と違う。

### 3.3 他に連打で2重に動く操作

Claude がコードを読んで確かめた。エミュレーターでは確かめていない。

| 操作 | 2重に動くか（コードから読んだ予想） | 根拠 | 扱い |
|---|---|---|---|
| 登録画面の「登録」ボタン | 動かない。ViewModel が送信中・登録済みなら処理を受け付けない。ボタンも送信中は無効になる | `TodoCreateViewModel.createTodo()` の `isSubmitting` / `isCreated` の判定（Claude） | 5章で連打して確かめる |
| 編集画面の「保存」ボタン | 動かない。登録と同じ | `TodoEditViewModel.updateTodo()` の `isSubmitting` / `isUpdated` の判定（Claude） | 5章で確かめる |
| 削除の確認ダイアログの「削除」 | 動かない。押すとダイアログを閉じ、ViewModel も削除中・削除済みなら受け付けない | `TodoDetailScreen` の `showDeleteConfirmation = false`、`TodoDetailViewModel.deleteTodo()` の `isDeleting` / `isDeleted` の判定（Claude） | 5章で確かめる |
| 詳細の「削除」ボタン（ダイアログを開く） | ダイアログは1つだけ。表示するかどうかを1つの値（`showDeleteConfirmation = true`）で持っているため、何回押しても同じ | `TodoDetailScreen`（Claude） | 5章で確かめる |
| 期日の選択の「OK」 | 2重に動いても結果は同じ。選んだ日付を入れてダイアログを閉じるだけ | `TodoCreateScreen`、`TodoEditScreen`（Claude） | 確認の対象にしない |
| 一覧・詳細・編集の「再試行」ボタン | 2回取得する可能性がある。ViewModel の `retry()` には読み込み中かどうかの判定がない。1回目で表示が読み込み中に変わり、ボタンが消えるまでの間の2回目は通る | `TodoListViewModel`、`TodoDetailViewModel`、`TodoEditViewModel` の `retry()`（Claude） | 今回は直さない。取得するだけで Todo は変わらず、後から届いた結果で表示が上書きされるだけ。困る場面が出てから別 Issue で扱う |

ViewModel の判定で2重送信が止まるのは、`createTodo()` などがメインスレッド（画面の処理を順番に1つずつ動かす流れ）で呼ばれ、送信中の印（`isSubmitting = true`）を立ててから通信を始めるためである。2回目の呼び出しは、1回目が印を立てた後に動くので、判定で止まる。

## 4. 実装ステップ

1. この設計メモをレビューする。
2. エミュレーターで、3つの操作の連打で画面が2重に開くことを確かめる（直す前の記録）。
3. 「Todoを作成」ボタン（`onCreateClick`）を `dropUnlessResumed` で包み、エミュレーターで確かめる。
4. 一覧の行の選択（`onTodoClick`）に `backStackEntry` の判定を入れ、エミュレーターで確かめる。
5. 詳細の「編集」（`onEditClick`）に 4 と同じ判定を入れ、エミュレーターで確かめる。
6. 5章の確認を行い、結果を記録する。
7. journal を作成し、README、文書一覧、基本設計、要件、ロードマップと関連 ADR を必要に応じて更新する。
8. `/codex:review --wait --base main --cwd /mnt/c/Users/gensh/projects/kotlin-todo` を実行し、指摘の採否を決めてから、コミット・push・PR 作成へ進む。

各ステップでは、`dropUnlessResumed` の中身とライフサイクルの状態をコードと結び付けて説明し、完成コードを一括で提示しない。

## 5. 確認方法

以下は確認予定であり、実施済みではない。エミュレーターでの確認、Claude によるコード・テストレポートの確認、Codex のレビューを区別して journal へ記録する。

### 手動確認

Pixel 8 のエミュレーターで行う。

- 連打（直す前）：3つの操作を素早く2回押し、同じ画面が2つ開くことを記録する
- 連打（直した後）：3つの操作を素早く2回・3回押しても、画面が1つだけ開く。1回戻ると前の画面に戻る
- 違う行の連打：一覧で違う2つの行を素早く続けて押しても、詳細が1つだけ開く
- 1回押したとき：今までどおり進める。戻ってきた後にもう一度押しても進める（戻った画面が再び `RESUMED` になり、操作を受け付けることの確認）
- 戻る操作：#74 の3画面の戻るボタンの連打で、前の画面を越えて戻らない
- 送信・削除の連打：「登録」「保存」、削除の確認ダイアログの「削除」を素早く2回押しても、登録・保存・削除は1回だけ行われ、Snackbar も1つだけ出る（3.3）。Todo の件数を一覧と API で確かめる
- 成功後：登録・編集・削除の成功後に自動で戻り、Snackbar が出て、一覧（編集では詳細も）が再取得される

### 自動テスト

- `testDebugUnitTest` の既存テストが成功する。今回は画面遷移の定義だけの変更で、ViewModel は変えないため、単体テストは追加しない
- 連打を確かめる UI テスト（Compose UI テスト）は今回は書かない。画面遷移を含む UI テストの仕組みがまだないため、ロードマップの「詳細・登録・編集の ViewModel、Repository、UI のテストを追加する」で扱う

## 6. 想定される詰まりポイント

- **`Lifecycle` の import**：`Lifecycle.State.RESUMED` を使うには `androidx.lifecycle.Lifecycle` の import が要る。`lifecycle-runtime-ktx` に含まれ、ライブラリは足さない
- **`backStackEntry` を取り違える**：一覧・詳細の `composable<...> { backStackEntry -> ... }` が受け取る `backStackEntry` を使う。`navController.currentBackStackEntry` と書くと、3.2 のとおり遷移が始まった後は新しい画面を指し、判定が正しく働かない
- **進んだ先から戻ったときに押せない**：遷移が終わって前の画面へ戻ると、前の画面は再び `RESUMED` になるはずだが、確かめていない。5章の「戻ってきた後にもう一度押しても進める」で確かめる
- **画面を開いた直後のタップ**：画面の切り替えアニメーションの途中は、新しい画面も `RESUMED` になっていない。開いた直後に素早く押すと、1回目のタップも捨てられる可能性がある。#74 の戻る操作では問題にならなかったが、進む操作でも確かめる
- **連打の速さ**：エミュレーターのマウス操作では、実機の指ほど速く押せない可能性がある。2重に開くことを再現できたかを、直す前の記録（4章の2）で確かめてから直す
