# Android 12 - Todo一覧ViewModel単体テストの実施結果

**実施日**: 2026-10-25、2026-10-26（U06強化後の再実行）  
**最終更新**: 2026-10-26  
**対応Issue**: [#63](https://github.com/GenkiHashioka/kotlin-todo/issues/63)  
**対象**: Windows側cloneの`test/android-todo-list-viewmodel`。基点`20b2785`からの作業ツリー（検証時点は未コミット）  
**計画**: [テスト設計](../../test-plans/android/12-todo-list-viewmodel-tests.md)  
**実装と学び**: [journal](../../journal/android/12-todo-list-viewmodel-tests.md)

## 環境と実施条件

- Windows 11、Android StudioとWindows側Git Bash。Android Studio側の`JAVA_HOME`は付属JBRを参照。
- ローカルJVMテスト。端末・Emulator、Backend・DBへの接続を使用しない。これらがプロセスとして停止していたかは記録対象としていない。
- JUnit 4.13.2、テスト用依存`kotlinx-coroutines-test:1.9.0`。依存確認時に開発者が共有した`kotlinx-coroutines-core`は1.9.0。
- 各ケースで新しいFake・Repository・ViewModelを生成する。`Dispatchers.Main`の差し替え、各テスト内の`finally`での`viewModelScope.cancel()`、`@After`での`resetMain()`を使用。
- テスト内でTodoDtoを直接生成し、DBへテストデータを投入しない。例外・応答保留はFakeだけで再現し、本番RepositoryやBackendへの一時変更はない。

## ケース結果

2026-10-25に開発者がテストクラス全体の6件PASSを報告した。文書レビュー後にU06のデータを強化し、2026-10-26にtestDebugUnitTest全体の成功ログを共有した。以下は強化後の最終ケース内容である。Codexはコードと生成済みJUnit XMLのケース名・集計を確認した。Codex自身はGradleを実行していない。

| ID | 対応するテスト関数 | 結果と判定 |
|---|---|---|
| U01 | `getTodos_whileWaiting_keepsStateLoading` | 生成直後と応答待ちのLoading、取得1回、応答完了後のEmptyを検証。PASS |
| U02 | `getTodos_whenNotEmpty_updatesStateToSuccess` | Successと取得一覧の内容一致を検証。PASS |
| U03 | `getTodos_whenEmpty_updatesStateToEmpty` | 空一覧でEmptyを検証。PASS |
| U04 | `getTodos_whenFails_updatesStateToError` | Fakeの通常例外を受けてErrorになることを検証。PASS |
| U05 | `retry_afterFailure_updatesStateToSuccess` | 同じViewModelでErrorからretryし、応答待ちのLoading、Successと一覧内容、取得合計2回を検証。PASS |
| U06 | `refresh_afterSuccess_updatesStateWithLatestTodos` | 同じViewModelでA・BのSuccessからrefreshし、Loading、更新したB・CのSuccessと一覧全体の一致、取得合計2回を検証。2026-10-26の再実行でPASS |

### U06を強化した理由と判断

2026-10-25の初回実装は、同じID `1`のTodoのタイトルだけを変更する1件の一覧でPASSした。これは内容更新を確認できる一方、取得結果の先頭1件しか保持しない不具合や、新しい一覧を古い一覧へ追加して消えたTodoを残す不具合を検出できない。複数件の確認を不要と判断したり、別テストへ延期する計画を立てたりしたわけではなく、確認範囲の制約として記録していた。

文書レビューでこの範囲が十分かを相談し、refreshの「再取得した一覧全体で置き換える」責務をより確実に検証するため、別ケースを増やさず既存U06のデータを強化する方針で合意した。2026-10-26に次のデータへ変更して再実行した。

| 段階 | Fakeが返す一覧 |
|---|---|
| 初回 | A（ID 1「単体テストを学ぶ」）、B（ID 2「Coroutineを学ぶ」） |
| refresh後 | 更新したB（ID 2「Coroutineのテストを学ぶ」）、C（ID 3「Composeを学ぶ」） |

初回と更新後それぞれでSuccessの一覧全体を比較し、Aが残っていないこと、Bの内容更新、Cの追加、複数件の内容とFakeが返した順序の保持を確認した。応答待ちのLoadingと取得合計2回の検証も維持した。

確認するのはViewModelに保持する一覧の置き換えであり、APIのソート仕様や実際の登録・削除APIの動作ではない。今回の責務に対してこの1ケースを強める範囲とし、全組み合わせの追加は行わない。

## 実行と証拠

開発者はAndroid Studioで対象クラス6件のPASSを報告後、WindowsのGit Bashで`android/`から次を実行し、成功ログを共有した。

```bash
./gradlew testDebugUnitTest
```

2026-10-25の共有ログの要点:

```text
BUILD SUCCESSFUL in 19s
26 actionable tasks: 1 executed, 25 up-to-date
Configuration cache entry stored.
```

2026-10-26、U06の強化とコメント修正後に、開発者が同じコマンドを実行した。共有ログの要点:

```text
Reusing configuration cache.
BUILD SUCCESSFUL in 2s
26 actionable tasks: 2 executed, 24 up-to-date
Configuration cache entry reused.
```

Codexは各確認時点で次の生成済みファイルを読み、6件・失敗0件・エラー0件・スキップ0件であることを確認した。強化後の最終XMLのtimestampは`2026-10-26T11:24:27.792Z`である。

- `android/app/build/test-results/testDebugUnitTest/TEST-com.genkihashioka.kotlintodo.ui.todo.list.TodoListViewModelTest.xml`
- tests: 6、failures: 0、errors: 0、skipped: 0。

共有ログは会話上の記録、XMLはローカルのビルド成果物であり、Git管理対象へ追加しない。共有ログにはUP-TO-DATEのタスクが含まれる。全タスクを強制再実行した記録とは扱わない。

## 静的確認と制約

CodexはFakeの分岐と呼び出し回数、状態・データの比較、各テストの準備と後始末を確認した。本番ソースに変更はなく、Androidの変更はテストソースとテスト依存追加である。

今回の合格は一覧ViewModelと既存Repositoryを経由した状態・データの確認に限る。実HTTP・JSON・DB、Compose、Navigation、Snackbar、登録・編集・削除の自動テスト、並行取得の競合・キャンセル専用ケース、今回の変更後のEmulator回帰テストは実施範囲に含めない。タイムアウト時の結果不明と整合回復も未対応のままである。
