# Android 09 - Todo操作の成功をSnackbarで伝える

**記録日**: 2026-10-18
**対応Issue**: [#55](https://github.com/GenkiHashioka/kotlin-todo/issues/55)
**ブランチ**: feat/android-todo-success-feedback
**設計メモ**: [Android 09](../../design-notes/android/09-todo-success-feedback.md)（実装前にレビュー）

## 実装結果

MainActivityの共通ScaffoldにSnackbarHostを配置した。登録・編集・削除に成功して元の画面へ戻れた場合、それぞれ「Todoを登録しました。」「Todoを更新しました。」「Todoを削除しました。」を表示する。文言はstrings.xmlで管理し、アクションなし・既定の短時間表示を使う。

NavHostは従来の対象へ再取得通知を保存し、popBackStackがtrueを返した場合に対応するonTodoCreated / onTodoUpdated / onTodoDeletedを呼ぶ。MainActivityはrememberCoroutineScopeからlaunchして表示を依頼する。既存のRoute、ViewModel、UiState、Repository、APIは変更していない。

## 学びと設計理由

### 配置・描画・状態を分ける

ScaffoldのsnackbarHost引数は、Snackbar用の枠に置くUIを受け取る。画面下部への配置はScaffoldが担当する。SnackbarHostは状態を使って表示・非表示を扱い、SnackbarHostStateは表示するメッセージなどを管理する。hostState引数は位置を指定するものではない。

### rememberとCompositionの寿命

Composableの該当箇所は再コンポーズで再実行され得る。通常のval宣言だけでは、実行のたびにオブジェクトを作ることを防げない。rememberで同じSnackbarHostStateを保持し、表示依頼先と描画側が使う状態を維持する。

再コンポーズは、監視している状態の変更などを受けて必要なUIを再計算する処理であり、UI全体の破棄ではない。その箇所がCompositionから取り除かれるとrememberの値は失われる。Activity再作成でもCompositionが作り直される。画面回転などでも起き得るため、アプリの再起動だけを指すわけではない。

共通Scaffold側に状態とScopeを置くことで、NavHost内の登録・編集・詳細画面を閉じても共通部分は残る。画面ごとにHostを作る必要がなく、戻った先でも表示できる。

### Coroutineとcallbackの責務

showSnackbarは表示終了まで待つsuspend関数である。通常のcallbackからrememberCoroutineScopeのlaunchを使って呼び出す。表示処理のCoroutineは共通部分のCompositionの寿命に従い、操作元画面の離脱だけではキャンセルされない。

NavHostは通知のタイミングと遷移、MainActivityは文言と表示を担当する。stringResourceはComposition内で呼び、通常のcallbackでは取得済みの文字列を使う。再取得通知用Booleanは成功の種類を区別できないため、Snackbarの契機には流用しない。

## 設計との差分とADR

設計どおり共通Hostと3つのcallbackを使った。一時的なLaunchedEffect(Unit)で表示を確認した後、その処理とimportを削除し、成功callbackへ接続した。

ADR 0023の再取得通知方針を継続する。今回の表示は既存の成功callbackと共通Scaffoldを利用する局所的な追加であり、新しいイベント配信基盤やデータ層の契約を導入していない。新規ADRは追加せず、表示責務と代替案は設計メモ、実装結果と判断は本journalおよび基本設計に記録する。

## 検証

### 開発者の手動確認報告

- 登録成功後、一覧へ戻って登録メッセージと更新内容が反映される。
- 編集成功後、詳細へ戻って更新メッセージと更新内容が反映される。
- 削除成功後、一覧から対象が消え、削除メッセージが表示される。
- メッセージは数秒で自動的に消える。
- 操作失敗時や削除確認のキャンセル時に成功メッセージが出ない。
- メッセージが消えた後、画面を開き直しても再表示されない。
- Snackbar表示中に次の操作を成功させると、対応するメッセージが順番に表示される。

### Codexの確認と検証の限界

Windows側の機能ブランチでMainActivity、NavHost、strings.xmlの差分を静的レビューした。文言とcallbackの対応、再取得通知の維持、popBackStackの条件、共通ScopeとHostの使用を確認し、git diff --checkを実行した。ビルド・エミュレータ操作はCodexでは実施していない。自動テストは追加していない。

最後の1件の削除、保存成功後に再取得だけが失敗する場合、Activity再作成、再コンポーズだけを意図的に発生させた場合は、個別の実施報告がないため確認済みとしない。

処理中の操作制御を含む機能横断の回帰テストは、実装が一通り揃った段階で実施する予定とする。これまでの機能ごとの手動確認と区別し、対象・手順・期待結果を整理する。自動化する範囲は、その際に検討する。

## 制約と次の課題

成功メッセージはAPI操作の成功を表し、戻り先の再取得完了を意味しない。再取得が失敗すると成功メッセージと既存のError表示が併存し得る。タイムアウトで結果を受け取れない場合は成功を推測しない。

表示中に別画面へ移動しても共通Hostの通知は続き得る。Activity・プロセスの再生成を越えた通知復元や厳密な一度だけの配信は保証しない。結果不明時の整合回復、自動テスト、Undoは今回の範囲外とする。

次はui/todoのパッケージ整理（#58）を扱う。
