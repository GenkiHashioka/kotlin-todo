## Summary

AndroidのTodo一覧から登録画面を開き、既存の`POST /todos`で新しいTodoを作成できるようにする。登録失敗時は入力を保持してエラーを表示し、成功時は一覧へ戻って最新データを取得する。

- タイトル、説明、カレンダーで選ぶ期日、優先度を入力する
- 優先度の初期値は`LOW`、ステータスは`NOT_STARTED`、`categoryId`は`null`
- ViewModelで通信失敗・送信中・登録成功を状態として扱い、送信中と登録済みの再送信を拒否する
- Routeの`LaunchedEffect`から成功callbackを呼び、NavHostで一覧へ戻る
- 戻り先の`SavedStateHandle`へ更新通知を渡し、一覧の`refresh()`でAPIから再取得する

一覧のViewModelは登録画面から戻っても保持されるため、戻る操作だけでは新しいTodoが表示されない。再取得した一覧でStateFlowを更新し、Composeへ反映する。

## Documentation

設計メモ（実装後に補完）、学習記録、README、要件・構成・ロードマップの進捗を更新する。着手前とPR前のドキュメント更新手順をAGENTS.mdへ明記する。

## Validation

開発者本人による手動確認:

- Android Studioでビルド成功
- 通信失敗時のメッセージ表示、入力保持、送信ボタンの再有効化、再送信時のエラー解除
- 登録成功後に一覧へ戻る
- 一覧に新しいTodoが表示される

自動テストは今回追加していない。Codexによる確認はコードレビューまで。

## Follow-up

- レイアウトと優先度の行全体のタップ対応
- 入力文字数の制約、項目別エラー表示、送信中の表示

Closes #49
