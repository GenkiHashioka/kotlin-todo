# Android 05 - Todoを登録し、一覧へ反映する

**ステータス**: 実装・手動確認・文書レビュー完了
**記録日**: 2026-10-03
**対応Issue**: #49
**ブランチ**: `feat/android-todo-create`
**設計メモ**: [Android 05: Todo登録](../../design-notes/android/05-todo-create.md)（実装後に補完）

## 目的

Androidから既存Ktor APIへTodoを登録する。入力状態、Coroutineの開始、通信失敗、送信中の状態、二重送信防止、成功後の画面遷移と一覧更新を、一つずつ実装しながら理解する。

## 作成したもの

- 一覧から登録画面へのNavigation
- タイトル、説明、カレンダーによる期日、優先度の入力
- `POST /todos`のRequest DTO、Retrofit API、Repositoryの登録処理
- 登録用のUiState、ViewModel、Factory、Route、Screen
- 通信失敗時のメッセージ表示と入力保持
- 送信中・登録済みの再送信を拒否するガード
- 登録成功後に一覧へ戻り、APIから最新の一覧を再取得する処理

優先度の初期値は`LOW`、登録時のステータスは`NOT_STARTED`、`categoryId`は`null`とする。説明が空白のみの場合は`null`へ変換し、期日は`LocalDate?`から`yyyy-MM-dd`形式の文字列または`null`へ変換する。

## 学んだことと設計理由

### callback名と接続先の関数名は一致しなくてもよい

Screenは`onSubmit: () -> Unit`で送信操作を通知する。Routeが`onSubmit = viewModel::createTodo`として接続する。関数参照を渡す時点では登録は実行されず、ボタン操作でcallbackが呼ばれたときにViewModelの処理が始まる。

Screenは表示と操作の通知、ViewModelは登録処理と状態更新を担当する。

### 通常の関数からCoroutineを開始する

ViewModelの`createTodo()`は通常の関数にし、その中の`viewModelScope.launch`からRepositoryのsuspend関数を呼ぶ。

`launch`はCoroutineの開始点であり、通信エラーを自動で画面状態へ変換する仕組みではない。通信で発生した例外は、`launch`内の`try / catch`で扱う。

### copyだけではStateFlowは更新されない

`uiState.value.copy(...)`は新しいUiStateを作るだけで、作成した値を保存しなければ状態は変わらない。

`_uiState.update { currentState -> currentState.copy(...) }`で新しい状態を保存する。最新の状態から必要な項目だけを変更するため、通信失敗時も入力値を保持できる。

### エラー状態と表示文言を分ける

最初は`errorMessage: String?`で表したが、今回扱う失敗表示は1種類なので、`hasSubmitError: Boolean`へ変更した。

ViewModelは失敗の有無を渡し、Screenが`stringResource()`で表示文言を取得する。エラーがないときは空文字のTextを配置するのではなく、`if (uiState.hasSubmitError)`でText自体の有無を切り替える。

失敗の種類を区別する要件が生じたら、enumなどで表すことを検討する。現段階では共通の失敗表示で十分と判断した。

### 送信中の解除とキャンセルの扱い

送信開始時に前回のエラーを消し、`isSubmitting = true`へ更新する。通信終了時は`finally`で`false`へ戻す。

`CancellationException`は通常の通信失敗へ変換せず再スローする。ViewModelの破棄などに伴うCoroutineのキャンセルを妨げないためである。

### ボタンの無効化とViewModelのガード

Screenはタイトルが空白のみの場合と送信中の場合に送信ボタンを無効化する。ViewModelも送信中なら早期returnし、追加のCoroutineを開始しない。

このガードは通常のUIからメインスレッドで呼ばれる構成を前提とする。送信中への更新は`launch`の前に行う。また、登録成功後に画面が戻るまでの再送信も防ぐため、`isCreated`がtrueの場合も拒否する。

これは同時送信を防ぐ仕組みであり、Backendで登録が完了した後にレスポンスだけ受け取れなかった場合の再試行まで重複を防ぐものではない。

### 成功後の遷移はLaunchedEffectからcallbackで伝える

Repositoryの登録処理が正常に戻った後に`isCreated = true`へ更新する。Routeは`LaunchedEffect(uiState.isCreated)`内で成功状態を確認し、`onCreated()`を呼ぶ。NavHostが実際の画面遷移を担当する。

Composable本体で直接遷移処理を呼ぶと、成功状態のまま再コンポーズされた場合にも呼ばれる可能性がある。LaunchedEffectはCompositionへ入ったときとキーが変わったときに実行され、同じComposition内ではキーが変わらない再コンポーズによって再実行されない。Compositionへ入り直した場合には再実行されるため、永久に一度だけ実行する仕組みではない。

### 一覧へ戻ることと、一覧データを更新することは別

`popBackStack()`で登録画面を取り除いても、一覧のBackStackEntryに紐づくViewModelは保持される。`init`は再実行されず、StateFlowには登録前の一覧が残る。

登録でBackendのDBが更新されても、一覧のStateFlowが自動で更新されるわけではない。再コンポーズだけでは、StateFlowに存在しない新しいTodoを表示できない。

```text
登録成功
  → 戻り先のSavedStateHandleへ再取得通知を保存
  → popBackStack()で一覧へ戻る
  → 一覧側が通知を受け取ってrefresh()を呼ぶ
  → APIから最新の一覧を取得
  → 一覧ViewModelがStateFlowを更新
  → Composeが新しい状態を受け取り、表示を更新
```

通知のキーは`todo_list_refresh_required`とし、Booleanを渡す。Todo本体を画面間で手動追加せず、APIから最新の一覧を取得する方針とした。

通知を受けて`refresh()`を呼んだ後はfalseへ戻す。これは通信成功を意味するのではなく、再取得要求を受け付けたことを意味する。`refresh()`は内部でCoroutineを開始し、通信完了を待たずに戻る。再取得失敗は既存の一覧のError表示と`retry()`で扱う。

`retry()`と`refresh()`はどちらも既存の`loadTodos()`を呼ぶが、失敗後の再試行と登録後の更新という目的を名前で区別した。

### Kotlinの書き方は対象に合わせて選ぶ

nullableな戻り先への保存は`?.set(key, true)`、存在する一覧の保存先への更新は`[key] = false`を使う。添字での代入はoperatorのsetを呼ぶ書き方であり、分割代入ではない。

構文を揃えるためだけにletを増やすより、対象のnull許容性に合わせた簡潔な書き方を選んだ。

## 動作確認

以下は開発者本人から確認済みの報告があった項目。Codexは差分をレビューし、ビルド・実機操作は実施していない。

- Android Studioでビルドが通ること
- 通信失敗時の表示、入力保持、送信ボタンの再有効化、再送信時のエラー解除
- 登録成功後に一覧へ戻ること
- 一覧の再取得により新しいTodoが表示されること

自動テストは今回追加していない。空白タイトル、期日の解除、各優先度の保存、連続登録などの個別確認結果は、PR提出前に本人が必要に応じて追記する。

## 理解度確認

対話で、次の流れを説明して理解を確認した。

- 一覧のViewModelが保持されるため、戻るだけでは初期化時の取得は再実行されない
- APIから最新のデータを取得し、StateFlowを更新してからUIへ反映する
- 再コンポーズはデータ取得処理ではなく、受け取った状態をUIへ反映する処理である

その他の概念は実装とレビューで扱い、文書は開発者がレビューした。

## 設計メモの作成時期

本来は着手前に設計メモを作成・レビューする運用だったが、今回の文書が欠けていたため2026-10-04に補完した。設計メモは対話と実装から判断を整理した記録であり、実装前の記録としては扱わない。今後はAGENTS.mdの着手前・PR前の確認手順を適用する。

## 後続の改善

- 入力欄・ボタンの余白や配置
- 優先度の文字を含む行全体を選択可能にする
- 入力文字数の制約と項目ごとのエラー表示
- 送信中の表示と入力操作の扱い
- ViewModel・Repository・UIの自動テスト

今回は機能面の学習を優先し、レイアウトとタップ範囲の改善は別の作業として扱う。
