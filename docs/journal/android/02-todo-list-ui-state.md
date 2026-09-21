# Android 02 - Todo一覧の画面状態を扱う

**ステータス**: 完了
**完了日**: 2026-09-22
**対応Issue**: #42
**設計メモ**: [Android 02: Todo一覧の画面状態を扱う](../../design-notes/android/02-todo-list-ui-state.md)

## 目的

Todo一覧取得中、取得成功、0件、失敗を区別し、通信に失敗してもアプリを終了させず、ユーザーが再試行できる画面にする。実装を通して、`sealed interface`による状態表現、Coroutineの例外処理、Composeへのイベントの渡し方を理解する。

## 作成したもの

- `Loading`, `Success`, `Empty`, `Error`からなる`TodoListUiState`
- UI Stateを切り替える`TodoListViewModel`
- Errorから一覧を再取得する`retry()`
- 4状態を描き分ける`TodoListScreen`
- RouteからScreenへ渡す`onRetry` callback
- Empty、Error、再試行用のstring resources

## 学んだこと

### `sealed interface`で状態を限定する

Todo一覧画面の状態を型ごとに分けると、ある時点で成立する状態を1つに限定できる。複数のBooleanで状態を表す場合に生じる、LoadingとErrorが同時にtrueになるような曖昧な組み合わせを避けられる。

`when`を`else`なしで書くことで、新しい状態を追加した際に既存の表示処理が不足している箇所をコンパイルエラーとして見つけられる。

### `data object`と`data class`

`Loading`, `Empty`, `Error`は状態ごとに追加データを持たないため、単一インスタンスで表せる`data object`を使った。`Success`は取得したTodo一覧を状態の一部として持つため、`data class`を使った。

### StateFlowの型を明示する

初期値が`TodoListUiState.Loading`だけの場合、型推論によって`MutableStateFlow<TodoListUiState.Loading>`として扱われると、後から`Success`などを代入できない。画面全体の状態型を保持することを示すため、次のように型引数を明示した。

```kotlin
MutableStateFlow<TodoListUiState>(TodoListUiState.Loading)
```

### Coroutineのキャンセルを握りつぶさない

`viewModelScope`はViewModelが破棄されると、そのScope内のCoroutineをキャンセルする。キャンセル時に使われる`CancellationException`まで通常の通信失敗として捕捉すると、`Error`への更新処理が実行され、キャンセルの伝播も止めてしまう。

そのため、`CancellationException`は再スローし、その他の`Exception`だけを画面の`Error`へ変換した。キャンセルは「バックグラウンドで処理を続けるため」の仕組みではなく、不要になった処理を協調的に終了させるための仕組みだと理解した。

### 状態とイベントを一方向に流す

RouteはViewModelを参照し、StateFlowをScreenへ渡す。ScreenはViewModelを知らず、状態に応じて描画する。再試行操作は`onRetry` callbackとしてScreenからRouteへ返し、RouteがViewModelの`retry()`を呼ぶ。

```text
状態: ViewModel → Route → Screen
操作: Screen → Route → ViewModel
```

ScreenをViewModelの取得方法から切り離すことで、表示を状態とcallbackだけから考えられる。

### Composeのレイアウト

`Box`は子要素を重ねたり、画面中央へ配置したりするときに使える。Error表示では`Column`を使い、説明文と再試行ボタンを縦に並べた。表示文言は`strings.xml`へ置き、Composableから`stringResource()`で取得した。

## 動作確認

- Backend起動中、Todoが1件以上ある場合にSuccessの一覧表示を確認
- Backend停止中、アプリが終了せずErrorが表示されることを確認
- Backendを起動して再試行し、Loadingを経てSuccessへ移ることを確認
- Repositoryから一時的に`emptyList()`を返し、Empty表示を確認
- Empty確認後、Repositoryの一時変更を元へ戻した
- debug buildが成功することを確認

## 開発中に詰まったこと

Android Studio上で`ComposableFunction1`へアクセスできない、`align`が未解決、lambdaの型を推論できないというエラーが同時に表示された。一方でGradle buildは成功していたため、ソースコードや依存関係そのものより、IDEの解析状態が古い可能性を確認した。Gradle Syncを再実行すると表示は解消した。

IDEのエラー表示とGradleのbuild結果が食い違う場合、表示された個別エラーをすぐ修正するのではなく、build結果、依存関係、Sync状態を分けて確認する。

## design noteとの差分

設計した4状態、再試行、CancellationExceptionの再スローを予定どおり実装した。EmptyはAPIデータを削除せず、Repositoryの戻り値を一時的に空リストへ置き換えて安全に確認した。

設計相談は実装前から会話で進めていたが、design noteのファイル化は実装と理解度確認の完了後になった。次の機能では、会話で設計が固まった時点で先にdesign noteを下書きし、実装後にjournalへ結果を残す。

## 現在の制約

- Errorは原因を区別せず、共通メッセージを表示する
- 再試行の多重実行を制御していない
- オフラインキャッシュがない
- UI StateはAPIの`TodoDto`を直接保持している
- Androidの自動テストがない

現在の要件では単純な一覧取得の失敗を回復できればよいため、これらは必要になるIssueまで保留する。

## 理解度確認

- [x] `sealed interface`で画面状態を分ける利点
- [x] `when`を網羅的に書く利点
- [x] `CancellationException`を再スローする理由
- [x] RouteとScreenを分け、callbackで操作を渡す理由
- [x] Errorから再試行したときの状態遷移
- [x] SuccessとEmptyを取得件数によって分ける流れ

Issue #42の実装について、4状態の意味、ViewModelの例外処理、RouteとScreenの責務、再試行時の状態遷移を自分の言葉で説明できることを対話で確認した。
