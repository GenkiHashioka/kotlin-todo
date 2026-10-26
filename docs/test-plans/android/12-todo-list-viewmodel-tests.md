# テスト設計 - Android 12: Todo一覧ViewModelの単体テスト

**作成日**: 2026-10-24  
**対応Issue**: [#63](https://github.com/GenkiHashioka/kotlin-todo/issues/63)

## 目的と対象

`TodoListViewModel`の初回取得・retry・refreshで、取得結果に応じた状態とデータになることを確認する。FakeやCoroutineの構成と実装順序は[実装設計](../../design-notes/android/12-todo-list-viewmodel-tests.md)を参照する。

実HTTP・JSON・DB・Compose表示・画面遷移・Snackbarは対象外。複数の取得処理が競合する条件やキャンセル専用の詳細ケースは今回扱わない。

## 前提条件とデータ

- Windows側のAndroidプロジェクトでJUnit 4のローカル単体テストとして実施する。Backend・DB・Emulatorは起動しない。
- 各テストでFake・Repository・ViewModelを新しく作る。Mainの差し替えは生成前に行い、終了時に後始末する。
- 非空の一覧は、ID・タイトルが異なるTodoを用意する。refreshでは更新前と更新後の内容を明確に変え、古いデータのままでも通る比較にしない。
- 通常の取得失敗はFakeから通常の例外を投げて再現する。Coroutineのキャンセル例外を通常の通信失敗の代わりに使わない。
- 取得中の確認ではFakeの応答を保留する。固定秒数の待機は使わない。

## 確認ケース

| ID | 前提・操作 | 期待結果 |
|---|---|---|
| U01 | 応答を保留するFakeでViewModelを生成し、取得処理を開始させる | 生成直後と、Fakeが呼ばれて応答待ちになった地点でLoading。応答待ちでは取得呼び出し1回。最後に保留を完了して後始末する |
| U02 | 非空の一覧を返すFakeで生成し、取得処理を完了させる | Successとなり、保持する一覧が用意した内容と一致する |
| U03 | 空一覧を返すFakeで生成し、取得処理を完了させる | Emptyとなる |
| U04 | 通常の例外を投げるFakeで生成し、取得処理を完了させる | Errorとなる |
| U05 | 初回取得をErrorにする。次の応答を保留した成功結果へ切り替え、retryする | 取得開始後はLoading、応答完了後はSuccessで新しい一覧と一致。取得呼び出しは合計2回 |
| U06 | 初回取得をSuccessにする。次の応答を別の一覧で保留し、refreshする | 取得開始後はLoading、応答完了後はSuccessで更新後の一覧と一致。取得呼び出しは合計2回で、古い一覧が残っていない |

## 実施順序・終了条件・記録

最初はU02を1本実装・実行し、U03・U04、U01、U05・U06の順に広げる。各段階で、準備・操作・検証が何を意味するか確認する。

終了条件は、上記ケースが通り、対象クラスの単独実行と`testDebugUnitTest`全体の実行が成功すること。実ネットワークやテストの実行順序に依存せず、失敗時にも次のテストへMainの差し替えや未完了の処理を残さないことをレビューする。

実施環境・コマンド・結果・判定は`docs/testing/android/12-todo-list-viewmodel-tests.md`へ実施後に記録する。現在の期待結果は実施済みを意味しない。学びは対応するjournalへ残す。
