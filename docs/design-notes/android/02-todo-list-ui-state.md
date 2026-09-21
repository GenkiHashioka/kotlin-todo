# 詳細設計メモ - Android 02: Todo一覧の画面状態を扱う

**バージョン**: 1.0
**最終更新**: 2026-09-22
**対応Issue**: #42

> このdesign noteは、実装前から会話で確認していた設計を、実装・レビュー・理解度確認の完了後に文書へ整理したもの。

## 1. 背景

Android 01では、Ktor APIから取得したTodoを一覧表示する最小のデータフローを構築した。一方、`TodoListUiState`はTodo一覧だけを持っていたため、取得中、取得結果0件、通信失敗を区別できなかった。Backend停止中に通信例外が発生すると、その例外が処理されずアプリが終了していた。

Issue #42では、Todo一覧画面が取り得る状態を型として表現し、ユーザーが現在の状況を判断できる表示と再試行操作を追加する。

## 2. スコープ

### このPRで作るもの

- `Loading`, `Success`, `Empty`, `Error`を表すTodo一覧のUI State
- UI Stateに応じたComposeの表示切り替え
- Errorから一覧取得をやり直す再試行ボタン
- CoroutineのキャンセルをErrorとして扱わない例外処理
- 表示文言のstring resource化

### このPRで作らないもの

- エラー原因ごとのメッセージ切り替え
- SnackbarやDialogによるエラー表示
- オフラインキャッシュ
- Pull to refresh
- 多重タップや重複リクエストの制御
- Androidの自動テスト
- Todo詳細とNavigation

## 3. 設計方針

### 3.1 UI Stateを`sealed interface`で表す

Todo一覧画面の状態は同時に1つだけ成立する。

```kotlin
sealed interface TodoListUiState {
    data object Loading : TodoListUiState
    data class Success(val todos: List<TodoDto>) : TodoListUiState
    data object Empty : TodoListUiState
    data object Error : TodoListUiState
}
```

`isLoading`, `isError`, `todos`のような複数プロパティを持つdata classでは、LoadingとErrorが同時にtrueになるなど、意味の曖昧な組み合わせを作れてしまう。`sealed interface`の各実装を状態として分けることで、画面が取り得るケースを限定する。

値を持たずインスタンスが1つでよい状態には`data object`を使う。取得した一覧を持つ`Success`だけを`data class`にする。

Composeでは`when`を`else`なしで使用する。状態を将来追加したとき、表示処理が不足している箇所をコンパイルエラーで検出できる。

### 3.2 状態遷移

初期状態と取得開始時の状態を`Loading`にする。

```text
起動
  ↓
Loading
  ├─ 取得失敗 ─→ Error
  │                ↓ 再試行
  │              Loading
  │
  └─ 取得成功
       ├─ 0件 ─────→ Empty
       └─ 1件以上 ─→ Success
```

再試行時も先に`Loading`へ戻し、再取得中であることを画面へ伝える。

### 3.3 例外とCoroutineのキャンセル

Repositoryから伝播した通常の`Exception`はViewModelで捕捉し、`Error`へ変換する。初回は原因別の表示を要件に含めないため、UI Stateには例外そのものやメッセージを保持しない。

`CancellationException`は、ViewModelが破棄された場合などにCoroutineを終了させるための制御信号でもある。広い`Exception`で捕捉して`Error`へ変換すると、終了すべき処理を通常の通信失敗として扱ってしまう。そのため先に捕捉して再スローし、それ以外の`Exception`だけを`Error`へ変換する。

### 3.4 RouteとScreenの責務

`TodoListRoute`はViewModelを知る境界として、StateFlowの監視と再試行イベントの受け渡しを担当する。

```text
TodoListViewModel.uiState ─→ TodoListRoute ─→ TodoListScreen
TodoListViewModel.retry() ←─ TodoListRoute ←─ onRetry
```

`TodoListScreen`はViewModelを直接参照せず、`TodoListUiState`と`onRetry: () -> Unit`を受け取る。これにより、Screenは状態の取得方法を知らず、渡された状態の描画に集中できる。

### 3.5 Composeのレイアウト

画面全体を`Box`で囲み、Loading、Empty、Errorを中央へ配置する。Error内では説明文と再試行ボタンを縦に並べるため`Column`を使う。Successでは既存の`LazyColumn`を維持する。

ユーザー向け文言はComposableへ直接書かず、`strings.xml`から`stringResource()`で取得する。

## 4. 実装ステップ

1. `TodoListUiState`をdata classから`sealed interface`へ変更する
2. ViewModelで取得開始、成功、空、失敗をそれぞれ対応する状態へ変換する
3. `CancellationException`を再スローする
4. ViewModelへ再試行操作を公開する
5. RouteからScreenへ再試行callbackを渡す
6. Screenで4状態を描き分ける
7. Empty、Error、再試行の文言をstring resourceへ追加する
8. buildとEmulatorで各状態を確認する

## 5. 確認方法

### ビルド

```powershell
cd android
.\gradlew.bat :app:assembleDebug
```

### 手動確認

- Backend起動中かつTodoが1件以上ある場合、Loading後に一覧が表示される
- Backend停止中の場合、アプリが終了せずエラー文言と再試行ボタンが表示される
- Error表示後にBackendを起動して再試行すると、Loadingを経て一覧が表示される
- Repositoryから一時的に空リストを返すとEmptyが表示される
- Empty確認後、一時変更を元へ戻す

自動テストはAndroidのテスト導入Issueで扱う。今回は4状態の手動確認とdebug buildを完了条件とする。

## 6. 想定される詰まりポイント

- `MutableStateFlow(TodoListUiState.Loading)`だけでは型が`Loading`に推論される場合があるため、`MutableStateFlow<TodoListUiState>`と明示する
- `Exception`を一括で捕捉すると`CancellationException`も対象になるため、先に捕捉して再スローする
- `TodoListScreen`からViewModelを直接呼ぶと表示と状態管理の境界が曖昧になるため、`onRetry` callbackを渡す
- `when`に`else`を置くと新しい状態の追加漏れをコンパイル時に検出できないため、4状態を明示する
- Android Studioの解析結果とGradle build結果が一時的にずれる場合は、まずGradle SyncでIDEのclasspath情報を更新する
