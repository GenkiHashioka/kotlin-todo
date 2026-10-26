# 詳細設計メモ - Android 12: Todo一覧ViewModelの単体テスト

**作成日**: 2026-10-24  
**対応Issue**: [#63](https://github.com/GenkiHashioka/kotlin-todo/issues/63)

## 1. 背景

手動回帰テストでCRUDと画面間の連携を確認した。次は一覧ViewModelに絞り、期待する状態と取得データをテストコードで繰り返し確認する。テストを作る過程で、依存先の差し替え、Coroutineの実行制御、状態の検証を一つずつ学ぶ。

現在の構成は[architecture](../../architecture.md#7-テスト戦略)、確認するケースは[テスト設計](../../test-plans/android/12-todo-list-viewmodel-tests.md)を参照。本書はテストコードの構成・責務・実装順序を扱う。

## 2. スコープ

### 今回作るもの

- `src/test`で動く一覧ViewModelのローカル単体テスト。
- 一覧取得の返却値・例外・完了タイミングを制御できるテスト用`TodoApi`。
- Coroutineのテスト実行環境と、各テスト終了時の後始末。

### 今回扱わないもの

登録・編集・削除の自動テスト、Compose表示、Navigation、Snackbar、実HTTP・JSON・DB、CI整備、DIライブラリ導入。テストのためだけにRepositoryをinterface化したり、継承可能にしたりしない。連続取得の競合やキャンセル専用の詳細なテストは今回の基本ケースから分ける。

## 3. 設計方針

### 依存先と責務

```text
テストコード
  → TodoListViewModel（既存実装）
  → TodoRepository（既存実装）
  → FakeTodoApi（src/testだけに配置）
```

ViewModelは状態を更新し、Repositoryはデータ取得を仲介する。実通信の境界である`TodoApi`だけを差し替える。既存のコンストラクタで渡せるため、本番コードにテスト専用の分岐を足す必要はない。

Repositoryを直接Mock化する案もあるが、今回は既存interfaceを使って自分で小さく実装し、差し替えの仕組みを理解する。実Repositoryも経由するため、このテストの成功だけでRepositoryとViewModelそれぞれを独立に検証したとは扱わない。

`FakeTodoApi`は一覧取得の処理を設定できる構成にし、返す一覧・投げる例外・待機を各テストで決める。再取得の確認用に呼び出し回数を持つ。今回使わないAPIメソッドは、誤って呼ばれたら明確に失敗させる。Fake、Repository、ViewModel、テストデータはテストごとに新しく用意し、実行順序へ依存させない。

配置は次を基本とする。共通部品は実際に重複する責務が出てから切り出す。

- `android/app/src/test/java/com/genkihashioka/kotlintodo/data/remote/api/FakeTodoApi.kt`
- `android/app/src/test/java/com/genkihashioka/kotlintodo/ui/todo/list/TodoListViewModelTest.kt`

### Coroutineの制御

JUnit 4は既存の依存を使い、追加は`kotlinx-coroutines-test`を基本とする。バージョンは導入前に現在解決されているCoroutinesとの整合を確認する。

ローカルJVMにはAndroidのMainスレッドがない。`viewModelScope`を使うため、ViewModelを作る前に`Dispatchers.Main`をテスト用Dispatcherへ差し替える。`runTest`とMain側で同じschedulerを共有し、`StandardTestDispatcher`で実行を明示的に進める。

- `runCurrent`で現在実行可能な処理を進める。
- `advanceUntilIdle`でscheduler上の実行可能な処理を進め切る。
- 取得中の観察では`CompletableDeferred`などでFakeの応答を保留し、テスト側から完了させる。保留された応答は`advanceUntilIdle`だけでは完了しない。

固定秒数の待機や実ネットワークの速度でテストを成立させない。テスト終了時は、失敗した場合もViewModelのCoroutineをキャンセルし、Mainの差し替えを解除する。`runTest`とは別に作られる`viewModelScope`の後始末も明示する。最初は準備・後始末を見える形にし、JUnit Rule等の共通化は役割を理解してから判断する。

### 検証する対象

公開された`uiState.value`を、操作前・取得開始後・応答完了後の必要な地点で確認する。`Success`という型だけでなく、Todoの内容まで比較する。private関数の呼び出し順には依存しない。

StateFlowは中間の値をすべて受信する保証がないため、全通知の列挙を前提にしない。取得中を調べたい箇所はFakeの応答を保留して状態を安定させる。最初からFlow収集用ライブラリは追加しない。

## 4. 実装ステップ

開発者が実装し、各段階でレビュー・実行確認を行う。一度に全ケースを書かない。

1. 既存JUnitとCoroutinesの依存関係を確認し、テスト用依存を追加してGradle同期する。
2. `FakeTodoApi`の最小構成を作る。実APIを呼ばず、指定した一覧を返す仕組みを理解する。
3. Mainの準備・後始末と、取得成功時に一覧が一致するテストを1本作り、実行する。
4. 空一覧・例外のケースを追加する。
5. 応答の保留を使って取得中の状態を確認する。
6. retry・refreshで再取得し、状態とデータが更新されるケースを追加する。
7. 対象クラスとAndroidアプリのローカル単体テスト全体を実行し、結果・学びを記録する。

## 5. 確認方法

具体的な前提・期待結果・終了条件は[テスト設計](../../test-plans/android/12-todo-list-viewmodel-tests.md)へまとめる。

Windows側のAndroidプロジェクトで開発者が実行する。Backend・DB・Emulatorは不要だが、Gradleのビルド設定で必要なSDK・JDK・`kotlinTodoApiBaseUrl`は設定済みであること。

Android Studioのテスト実行、またはWindowsのGit Bashで`android/`から次を実行する。

```bash
./gradlew testDebugUnitTest --tests 'com.genkihashioka.kotlintodo.ui.todo.list.TodoListViewModelTest'
./gradlew testDebugUnitTest
```

実施条件・コマンド・結果は`docs/testing/android/12-todo-list-viewmodel-tests.md`へ、学び・判断・差分は同じ連番とtopicのjournalへ実施後に記録する。開発者による実行とCodexの静的レビューを区別する。

参照: [Android公式のCoroutineテストガイド](https://developer.android.com/kotlin/coroutines/test)。

## 6. 想定される詰まりポイント

- ViewModel生成時の`init`で取得が始まるため、生成後にMainを差し替えても遅い。
- Dispatcherのschedulerが異なると、テスト側で進めてもViewModel側が進まない。
- 生成直後の初期値だけでは、取得処理に到達した後もLoadingかは分からない。Fakeが呼ばれ、応答を待っている地点も確認する。
- テスト用のTodoDtoも必須フィールドを満たす。DBの実データや自動採番には依存しない。
- テストが通っても、UIや実際の通信が正しいという証明にはならない。手動回帰の記録と検証範囲を区別する。
