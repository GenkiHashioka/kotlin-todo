# Android 07 - 詳細画面からTodoを削除する

**記録日**: 2026-10-17
**対応Issue**: [#54](https://github.com/GenkiHashioka/kotlin-todo/issues/54)
**ブランチ**: feat/android-todo-delete
**設計メモ**: [Android 07: Todo削除](../../design-notes/android/07-todo-delete.md)（2026-10-12作成、実装前にレビュー）

## 実装結果

- TodoApiのDELETEとRepositoryのdeleteTodoを追加した。既存Backendの200 OKと削除したTodoのレスポンスをTodoDtoで受ける。
- 詳細取得成功時だけ削除ボタンを表示し、確認ダイアログで承認後に通信する。キャンセル・dismissでは削除しない。
- SuccessにisDeleting、hasDeleteError、isDeletedを追加し、削除失敗でも詳細データを保持する。
- ViewModelは取得成功前・削除中・削除済みの呼び出しを拒否し、通信前に削除中の設定と前回エラーの解除を行う。
- 通常の例外は削除失敗、CancellationExceptionは再スロー、finallyで削除中を解除する。
- 削除成功をRouteのLaunchedEffectからNavHostへ通知し、一覧へ再取得通知を保存して詳細を閉じる。
- 削除中・削除済みの編集・削除・戻る操作を抑止し、削除中の文言を表示する。
- ApiClientの共有OkHttpClientへcallTimeout 30秒を設定し、Retrofitへ接続した。全Todo APIへ適用される。

## 学びと設計理由

### RepositoryとAPIの責務

TodoApiはHTTP契約、RepositoryはViewModelへTodo操作を提供する境界、ViewModelは処理の開始判断と画面状態への反映を担当する。現在のRepositoryは委譲だけでも、HTTP呼び出しをViewModelへ直接持ち込まず既存の境界を維持する。

### 取得成功と削除成功を分ける

Successは詳細取得の成功を表す。削除失敗時に取得用のErrorへ切り替えると詳細が表示されなくなるため、Successのtodoを保持しhasDeleteErrorで表す。copyで一部の値を変え、updateでStateFlowへ保存する。削除成功時のisDeletedはfinallyでも保持し、自動遷移までの追加送信を防ぐ。

### ScreenとRouteをcallbackで接続する

Screenは確認後にonDeleteClickを呼び、RouteがviewModel::deleteTodoへ接続する。関数参照は、その場で実行せず後で呼ぶために渡す。IDは詳細ViewModelが既に保持しているため渡し直さない。

ダイアログの表示はScreenのrememberした状態として管理し、確認時は閉じてから削除callbackを呼ぶ。通信結果や遷移とは分ける。ダイアログ表示状態の画面再生成時の復元は追加していない。

### canGoBackは保持せず状態から計算する

canGoBackはuiStateから求めるローカルのvalとした。ボタン操作でこの変数へ代入するのではなく、ViewModelの状態更新を受けた再コンポーズで計算し直す。rememberや別のMutableStateは必要ない。

最初の条件ではSuccess以外も戻れなくなっていた。Successでなければ戻れ、Successなら削除中・削除済み以外で戻れる条件へ修正した。

画面上の戻るボタンはenabled = canGoBack、BackHandlerはenabled = !canGoBackとする。BackHandlerのenabledは戻る操作を受け取る処理の有効・無効を表すため、条件が逆になる。何もしないHandlerで操作を消費し、通常時はNavigationへ任せる。NavHostのpopBackStackによる成功後の自動遷移は妨げない。

### 通信を始めた後に戻るリスク

画面を戻ってViewModelが破棄されても、Backendの処理完了を取り消せる保証はない。成功前に離脱すると一覧通知も失われ得るため、削除中と削除済みの戻る操作を抑止した。同じ問題は登録・編集にもあり、それらの戻る制御は別の修正Issueで扱う。

### 操作の復帰と結果の確定は異なる

待機から操作を再開するため、HTTP呼び出し全体のタイムアウトを設定した。OkHttpClientを作るだけでは適用されず、Retrofitへのclient接続が必要だった。またobjectの初期化順序に合わせ、httpClientをretrofitより先に宣言した。

検証では、タイムアウト後に削除失敗表示と操作再開ができた一方、Backendではその後に削除が完了した。残った詳細からの編集更新も失敗した。タイムアウトは削除されなかったことを意味せず、Androidが結果を受け取れなかった場合もある。

現在は結果不明時の再取得・整合回復を実装していない。「削除に失敗しました」という文言も実際の削除結果を断定してしまうため、結果確認の導線とともに後続で見直す。戻る操作を止める対策やタイムアウトだけでこの問題を解決したとは扱わない。

## 設計との差分・ADR

既存詳細画面への追加、取得失敗と削除失敗の区別、一覧の通知・再取得は当初設計に沿って実装した。設計で実装時に確認する予定だった通信中の離脱について、戻る操作の抑止を採用した。ユーザーから長時間待機の懸念があり、削除中表示と共通HTTPタイムアウトを追加した。

DELETEのレスポンス契約はADR 0006を継続し、ADR 0023へ削除への適用を追記した。HTTP共通設定は削除に限らず全APIへ影響する判断なので、ADR 0024として記録した。元のdesign-noteは着手時の意図として保持し、実装結果で書き換えていない。

## 検証

### 開発者の手動確認報告

- 削除成功後に一覧へ戻り、削除が反映されること。
- 確認のキャンセル、ダイアログ外タップ、ダイアログ表示中の戻る操作ではTodoが残ること。
- 削除失敗で詳細とメッセージを表示し、Backend復旧後に再操作できること。
- 別のTodoを続けて削除しても一覧が更新されること。
- 最後の1件を削除すると一覧がEmpty表示になること。
- 一時的なViewModelのdelayを使い、削除中の操作制御を確認したこと。
- AndroidのcallTimeoutを一時的に2秒、BackendのDELETE前のdelayを5秒として検証し、タイムアウト後に失敗表示と戻る・編集・削除操作が可能になること。
- タイムアウト後にBackendで削除が完了し、古い詳細からの編集更新が失敗することも確認したこと。
- 確認用設定を戻した後も動作確認したこと。

### Codexが行った確認

Windows側feat/android-todo-deleteのAPI・Repository・状態・ViewModel・Screen・Route・NavHostの差分を静的レビューし、git diff --checkを実行した。callTimeoutが30秒でRetrofitへ接続されていることと、Android内に一時的なdelayがないことを確認した。実行用WSL cloneのBackendに確認用delayや未コミットのBackend差分が残っていないことも確認した。

CodexはAndroidビルド・エミュレータ操作・タイムアウトの実行検証を行っていない。自動テストは追加していない。既存の登録・編集全体の回帰確認と、取得中・取得失敗時の戻る操作など、個別の実施報告がない項目は手動確認済みと断定しない。

## 後続課題

- 登録・編集・削除の成功フィードバック（Issue #55）。
- 登録・編集の保存中の戻る制御。削除とは別の修正Issueとする。
- 結果不明時の再取得と整合回復、既に削除済みの場合の操作、結果を断定しないエラー文言。
- ui/todoの画面別パッケージ整理。CRUDが揃った段階で見直す。
- レイアウト、処理状態の表示の改善、自動テスト、プロセス再生成や通信断への対応。
