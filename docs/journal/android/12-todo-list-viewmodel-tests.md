# Android 12 - Todo一覧ViewModelのローカル単体テスト

**記録日**: 2026-10-25  
**最終更新**: 2026-10-26  
**対応Issue**: [#63](https://github.com/GenkiHashioka/kotlin-todo/issues/63)  
**ブランチ**: `test/android-todo-list-viewmodel`  
**実装設計**: [Android 12](../../design-notes/android/12-todo-list-viewmodel-tests.md)（実装前に開発者がレビュー）  
**テスト設計**: [Android 12](../../test-plans/android/12-todo-list-viewmodel-tests.md)  
**実施結果**: [6ケースと実行記録](../../testing/android/12-todo-list-viewmodel-tests.md)

## 実装結果

開発者が`src/test`に`FakeTodoApi`と`TodoListViewModelTest`を実装し、段階ごとにレビューと実行確認を行った。初回取得のLoading / Success / Empty / Error、エラー後のretry、成功後のrefreshを6件で検証した。

本番のViewModel・Repository・TodoApiは変更していない。既存Repositoryへテスト用TodoApiを渡し、実通信せずに結果を制御した。既存JUnit 4.13.2を使い、依存確認時のCoroutines 1.9.0に合わせて`kotlinx-coroutines-test:1.9.0`を追加した。

Fakeは返却一覧、投げる例外、保留する応答をプロパティで受け取る。優先順位は例外、保留応答、通常の返却一覧。呼び出し回数は外から参照でき、変更はFake内だけに制限した。使用しないAPIメソッドでは`error()`を使う。

## 学びと設計理由

### interfaceを使った依存先の差し替え

Repositoryは具体的なRetrofitの生成物ではなく`TodoApi`を受け取るため、同じinterfaceを実装したFakeも渡せる。テスト用分岐やDIライブラリ、Repositoryのinterface化を足さずに既存の依存注入を利用できた。今回のテストは実Repositoryも経由し、Repository自体を独立に検証するものではない。

### Main Dispatcherとテストの実行管理

ローカルJVMテストではAndroidの画面を起動しない。`viewModelScope`が使うMainを、ViewModel生成前に`StandardTestDispatcher`へ差し替える。`runTest`と同じschedulerを共有し、応答待ちまで進める箇所で`runCurrent()`、応答完了後に実行可能な処理を進め切る箇所で`advanceUntilIdle()`を使った。

固定秒数の待機ではなく、テスト側で進行を制御する。初期値のLoadingだけでなく、Fakeが実際に呼ばれて応答を待つ地点のLoadingと呼び出し回数を比較した。

### CompletableDeferredは1回の応答に使う

`CompletableDeferred`の`await()`で応答を保留し、`complete()`で結果を渡した。refreshの実装途中では、完了済みの同じ箱を再設定しても未完了へ戻らないことを確認した。2回目には新しい箱を作る。完了済みなら`await()`は待たずに以前の結果を返し、再度の`complete()`では結果を変更できない。

retryの実装途中では、初回の例外設定が残っているため再度Errorになる原因をコードから追った。応答用の箱を設定しても別のプロパティは変わらず、例外を解除してからretryする必要がある。同じViewModelを使い、エラー状態から復帰できることを確認した。

### 検証と後始末

`Success`の型だけでなく、data classの等価比較で一覧の内容まで検証した。refreshの初回実装は単一Todoのタイトル変更だったが、文書レビューで、先頭1件だけ保持する処理や古い一覧へ追加する処理でも見逃し得ることを検討した。

一覧全体の置き換えを検証する責務に合わせ、初回をA・B、更新後を更新したB・Cとするデータへ変更した。これにより、消えたAを残さず、Bを更新し、Cを含む複数件の一覧と一致することを1つの既存ケースで確認できる。`copy()`でBのIDや他の値を引き継いだ別オブジェクトを作り、タイトルだけを変更した。

複数件の確認を不要としたり別課題へ延期したりせず、既存U06を強める判断とした。比較する順序はFakeの返却順の保持であり、APIのソート仕様や実際の登録・削除APIは対象外。全組み合わせを追加することは今回の終了条件にしない。

各テストの`finally`でViewModelのCoroutineをキャンセルし、JUnitの`@After`でMainの差し替えを解除した。Coroutineの終了とDispatcherの設定復元は別の責務であり、応答完了前にassertionが失敗しても後始末する。

## 設計との差分と採否

- 計画の非空一覧成功ではなく、データ準備の少ないEmptyを最初の1本とし、実行環境の理解とTodoDtoの生成を分けた。
- Fakeの取得処理を関数として設定する案に対し、実装は返却値・例外・保留応答を順に追加する構成とした。今回の6件では動作を追いやすく、複雑な共通化は導入しなかった。
- U06は初回の単一Todo更新の検証から、文書レビューを経て複数件の置き換えの検証へ強化した。ケース数は6件を維持し、2026-10-26に再実行した。変更理由・データ・結果は実施結果へ記録した。
- テストデータとtry/finallyには重複がある。今は各ケースの準備・操作・検証を直接読める構成を維持し、共通化は必要になった段階で判断する。
- JUnit 4は既存設定を利用した。JUnit 6の利用不可を理由とした選定ではなく、今回は状態管理とCoroutineのテストを学習対象に絞った。JUnitの世代移行は行っていない。

既存の依存関係・画面別配置を変えず、今回のテスト構成に閉じる判断は本書と実装設計へ残した。全画面共通のテスト基盤を確定したわけではないため、今回の追加ADRは作成していない。

## 検証

開発者が2026-10-25に対象クラス6件のPASSと、Windows Git Bashでの`./gradlew testDebugUnitTest`の成功を報告した。U06を強化した2026-10-26にも同じタスクの成功ログを共有した。Codexは修正コードを静的レビューし、各確認時点の生成済みJUnit XMLで6件・失敗0件・エラー0件・スキップ0件を確認した。Codex自身はAndroidのビルド・テスト・Emulator操作を行っていない。

具体的な条件・ケース対応・コマンド・証拠・制約は[実施結果](../../testing/android/12-todo-list-viewmodel-tests.md)を参照する。

## 残る課題と次の作業

実HTTP、Compose表示・画面連携、他画面の状態管理、取得競合やキャンセル専用ケースは今回の自動化対象外。タイムアウト時の整合回復も別課題である。全画面のテストやCIを一律の前提にせず、次は[Issue #65](https://github.com/GenkiHashioka/kotlin-todo/issues/65)で対象と終了条件を具体化し、UI・操作性の改善へ進む。
