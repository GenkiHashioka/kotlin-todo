# 詳細設計メモ - Android 05: Todoを登録し、一覧へ反映する

**バージョン**: 1.0（開発者レビュー済み、実装後の補完）
**作成日**: 2026-10-04
**対応Issue**: #49
**作成時点**: 実装後の補完。対話と実装から設計判断を整理したもので、着手前に作成・承認された記録ではない。

## 1. 背景

Todo一覧と詳細の表示に続き、既存Ktor APIのPOST /todosをAndroidから呼び出す。入力、通信の失敗、送信状態、成功後の遷移と一覧更新を分けて学ぶ。要件はrequirements.mdのAndroidクライアントに対応する。

## 2. スコープ

- 一覧から登録画面へ遷移し、タイトル、説明、任意の期日、優先度を入力する。
- 優先度の初期値はLOW、ステータスはNOT_STARTED、categoryIdはnull。
- カレンダーで期日を選択・解除する。説明の空白入力はnullとして送る。
- 通信失敗時は入力を残し、共通メッセージを表示して再送信できるようにする。
- 送信中と登録済みの追加送信を拒否する。
- 成功時は一覧へ戻り、APIから再取得して新しいTodoを反映する。

編集・削除、Categoryの選択、Backendの拡張、DIライブラリ、項目別エラー、自動テスト、レイアウトや優先度の行全体のタップ対応は今回の対象外とする。

## 3. 設計方針

### 責務とデータフロー

Screenは入力と描画、RouteはViewModelの状態監視とcallbackの接続、ViewModelは登録と状態更新、RepositoryはAPI呼び出し、NavHostは画面遷移と更新通知を担当する。

入力を持つTodoCreateUiStateへisSubmitting、hasSubmitError、isCreatedを追加する。画面全体をErrorへ置き換えず、入力を保持する。今回の失敗表示は1種類なのでBooleanとし、表示文言はScreenがstringResourceで取得する。複数種類のエラーが必要になった時点でenumなどを検討する。

### 通信と二重送信防止

ViewModelの通常のcreateTodo()からviewModelScope.launchを開始し、Repositoryのsuspend関数を呼ぶ。送信中への更新とガードはlaunchの前に行う。通信はtry / catchで扱い、CancellationExceptionは再スロー、通常のExceptionは失敗状態へ変換する。finallyで送信中を解除する。

送信内容は開始時の入力状態から作成する。UIのボタン無効化に加えてViewModelでも送信中・登録済みを拒否する。Backendの処理完了後にレスポンスを受け取れなかった場合の再試行まで重複を防ぐ保証はない。

### 成功後の遷移

Repositoryが正常に戻った後だけisCreatedをtrueにする。RouteのLaunchedEffect(isCreated)からonCreatedを呼び、NavHostでpopBackStackする。Composable本体で直接遷移処理を呼ぶ形を避け、状態に応じた副作用として扱う。

### 一覧の再取得

一覧ViewModelは戻ったときも保持されるため、initの取得だけでは更新されない。Todoを手動追加する方法もあるが、今回はAPIから最新の一覧を再取得する。

NavHostで戻り先のSavedStateHandleへtodo_list_refresh_required = trueを保存してから戻る。一覧側がgetStateFlowで通知を受け取り、LaunchedEffectからrefresh()を呼んで通知をfalseへ戻す。refresh()はCoroutineを開始する通常の関数であり、falseへの更新は通信完了を表さない。

```text
登録成功 → 更新通知を保存 → 一覧へ戻る
→ refresh() → APIから最新の一覧を取得
→ StateFlow更新 → Composeの表示更新
```

retryとrefreshは呼び出す意図を名前で区別し、実際の取得は共通のloadTodosへ委譲する。再取得失敗時は既存の一覧Error表示とretryで扱う。

## 4. 実装ステップ

以下は実装と対話で進めた順序を整理したもの。

1. API、Request DTO、Repositoryの登録処理を追加する。
2. 登録画面・状態・ViewModel・Factory・Destinationを追加し、入力を接続する。
3. launch内で例外を扱い、送信中の更新と再送信ガードを追加する。
4. Routeから送信callbackを接続し、Screenでリソースの失敗文言を表示する。
5. 成功状態をRouteのLaunchedEffectで受け取り、NavHostで一覧へ戻す。
6. 一覧に更新通知を渡し、refreshと通知の処理済みへの変更を接続する。
7. 動作確認、journal、README・構成・要件・ロードマップの状況更新を行う。

## 5. 確認方法

Android Studioでビルド・実行する。手動確認は以下を対象とする。実施済みの範囲はjournalに記録し、未確認の項目を確認済みにしない。

- 空白タイトルでは送信できない。
- 説明・期日の未設定、日付の選択と解除、優先度の変更が登録へ反映される。
- 通信失敗時に入力が残り、エラー表示と送信ボタンの再有効化が行われる。
- 再送信時に前回のエラーが消える。
- 送信中の連打で追加の登録を開始しない。
- 登録成功後に一覧へ戻り、新しいTodoが表示される。
- 続けてもう1件登録しても通知が働く。
- 登録せず戻った場合は更新通知を送らない。

自動テストは今回追加せず、後続のテスト学習で扱う。

## 6. 想定される詰まりポイント

- copyは元の状態を変更しない。作成した状態をMutableStateFlowへ保存する。
- try / catchは通信が実行されるlaunchの内側へ置く。
- stringResourceはScreenで呼び、ViewModelには表示文言の取得を持たせない。
- finallyで送信中を解除しても、登録済みの再送信は別のガードで拒否する。
- LaunchedEffectはCompositionへ入り直すと再実行される。永久に一度だけ動く仕組みではない。
- 戻るだけでは一覧データは更新されない。再コンポーズとAPI取得を区別する。
- 更新通知は同じキーで保存・取得し、受け付けた後falseへ戻す。
