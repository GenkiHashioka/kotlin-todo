# 詳細設計メモ - Android 09: Todo操作の成功をSnackbarで伝える

**バージョン**: 0.1
**作成日**: 2026-10-17
**対応Issue**: [#55](https://github.com/GenkiHashioka/kotlin-todo/issues/55)

## 1. 背景

登録・編集・削除は、成功後に画面を戻して詳細・一覧を再取得する。ユーザーが操作の成功を判断しやすいよう、成功を短いメッセージでも明示する。

Snackbarは画面の下部へ一時的に表示する通知で、確認ダイアログのように操作を止めない。今回は短時間で消える成功メッセージだけを使い、操作ボタンやUndoは追加しない。

関連文書: [要件](../../requirements.md)、[基本設計](../../architecture.md)、[ロードマップ](../../roadmap.md)、[ADR 0023: 成功後の通知と再取得](../../decisions/0023-navigation-refresh-notification.md)、[Android 08: 保存中の戻る制御](08-todo-submit-navigation.md)、[Snackbarの公式説明](https://developer.android.com/develop/ui/compose/components/snackbar)。

## 2. スコープ

### この機能で扱うもの

| 操作 | 成功後の戻り先 | 表示文言の案 |
|---|---|---|
| 登録 | 一覧 | Todoを登録しました |
| 編集 | 詳細 | Todoを更新しました |
| 削除 | 一覧 | Todoを削除しました |

- 既存のMainActivityのScaffoldへ共通のSnackbarHostを1つ配置する。
- APIの成功を受けた既存callbackから、再取得通知と戻る処理の後に表示を依頼する。
- 操作失敗・キャンセル・単に戻る場合は成功メッセージを出さない。
- 通常の再コンポーズや、戻り先への再訪だけで成功メッセージを繰り返し表示しない。
- 保存・削除の成功と、その後のデータ再取得の成功を区別する。
- 既存の入力保持、送信・削除中の操作制御とNavigationを維持する。

### 今回扱わないもの

完了専用画面、Toast、Undo・Snackbar内のアクション、エラー表示全体のSnackbar化、通知の履歴、プロセス再生成後の通知復元、厳密な一度だけの配信保証、タイムアウト後の結果確認・整合回復、Backend変更、パッケージ整理（#58）、新しいイベント配信基盤やライブラリ。

## 3. 設計方針

### 3.1 表示方法の選択

成功を伝えるためにSnackbarを採用する。画面遷移を増やさず、ユーザーの確認操作を要求せずに短い結果を伝えられるためである。

完了画面は画面定義と戻る操作を増やす。Toastも短い通知に使えるが、既存のComposeのScaffoldに表示場所を組み込めるSnackbarを今回は使う。将来の表示要件が変わった場合は見直す。

### 3.2 MainActivityに共通の表示場所を置く

現在MainActivityは全画面を囲むScaffoldを持つ。そのsnackbarHostへSnackbarHostを配置し、SnackbarHostStateをrememberで保持する。Scaffoldは配置、SnackbarHostは通知の描画、SnackbarHostStateは表示待ち・表示中のSnackbarを管理する。

表示依頼にはSnackbarHostStateのshowSnackbarを使う。この関数は表示終了を待つsuspend関数なので、MainActivityのCompositionに紐づくrememberCoroutineScopeからlaunchする。表示要求を送るcallback自体は通常の関数とし、表示の完了を画面遷移の条件にしない。

共通のScaffoldは戻り先でも残るため、削除・登録・編集元の画面を閉じてもSnackbarの表示用Coroutineは、その画面の破棄を理由にキャンセルされない。SnackbarHostStateやCoroutineScopeをViewModelへ渡さない。

| 要素 | 責務・変更方針 |
|---|---|
| MainActivity | Hostと表示状態・CoroutineScopeを保持し、成功文言を解決して表示を開始する |
| KotlinTodoNavHost | 既存の成功callbackで再取得通知と戻る処理を行い、成功メッセージの表示をMainActivityへ依頼する |
| 各Route | 既存の成功フラグとLaunchedEffectからNavHostへ通知する。変更は原則不要 |
| 各Screen / ViewModel / UiState | 既存の表示・操作制御・成功状態を維持し、Snackbar専用の状態は追加しない |
| TodoRepository / TodoApi | 変更しない |
| strings.xml | 登録・更新・削除の成功文言を管理する |

一覧・詳細ごとにScaffoldとHostを追加する方法もあるが、今回は既存の共通Scaffoldを利用する。表示が続いている間にユーザーが別の画面へ移動した場合も、共通Hostでは短いメッセージが引き続き表示され得る。画面に厳密に限定した表示は今回の要件に含めない。

### 3.3 成功callbackから表示を依頼する

NavHostはMainActivityから、登録成功・編集成功・削除成功の表示依頼用callbackをそれぞれ受け取る案とする。例えばonTodoCreated、onTodoUpdated、onTodoDeletedという通常の引数なしcallbackとし、既存RouteのonCreated / onUpdated / onDeletedとは役割と接続位置を区別する。

MainActivityはComposition内でstrings.xmlの文言を取得し、それを使う表示処理をcallbackとして渡す。onClickや通常のcallback内からstringResourceを直接呼ばない。

NavHostの既存の成功処理は次の順にする。

1. 既存と同じ対象へ再取得通知を保存する。
2. popBackStackで元の画面へ戻る。
3. 戻る処理に成功した場合、対応する表示依頼用callbackを呼ぶ。

表示callbackはMainActivityのCoroutineを開始して戻るため、Snackbarが消えるまでNavigationを待たせない。CoroutineやSnackbarHostStateそのものをNavHostへ渡す必要もない。

```text
API成功
  → ViewModelが成功状態を更新
  → RouteのLaunchedEffectから既存の成功callback
  → NavHostが再取得通知を保存して戻る
  → MainActivityへ表示を依頼
  → 共通HostでSnackbarを短時間表示
```

成功の種類が3つと少ないため、最初から共通イベントクラスやenum、Channel、SharedFlowを追加しない。callbackの増加が実際の問題になった場合は再検討する。

### 3.4 再表示と連続操作の扱い

Composable本体でshowSnackbarを直接呼ばず、既存の成功callbackを契機に表示を依頼する。単なる再コンポーズではcallbackを再実行しないため、同じ成功状態から表示を繰り返さない。通常の成功処理では操作元の画面をBack Stackから取り除くので、戻り先への再訪だけで元の成功callbackを呼び直さない。

既存の再取得用Booleanを表示の契機には使わない。一覧の再取得通知だけでは、登録と削除を区別できない。編集後に一覧へ戻ったときにも更新メッセージが再表示される設計にしない。

表示は短時間、アクションなしとする。同じHostへの複数のshowSnackbar呼び出しは順番に表示されるため、連続操作でも対応するメッセージを確認する。通知の永続化やプロセス再生成をまたぐ一度だけの配信は保証しない。Activity再生成時に表示途中のSnackbarが消える場合も、成功の処理を再実行して復元しない。

### 3.5 成功とデータ再取得は別に扱う

SnackbarはAPIの登録・更新・削除が正常に戻ったことを伝える。戻り先の再取得完了を待つ通知ではない。保存成功後に再取得だけ失敗した場合は、成功Snackbarと戻り先の既存Error表示が併存し得る。

再取得失敗は既存のretryで扱い、成功通知を再試行しない。タイムアウトなどでAPIの成功を受け取れなかった場合も、Backendの結果を推測して成功Snackbarを出さない。結果不明時の整合回復は別課題である。

### 3.6 ADRの扱い

再取得通知はADR 0023を継続する。今回は共通Scaffoldで成功メッセージを表示する責務の選択をこの設計メモに記録する。実装後に、共通Hostとcallbackの方針をADRへ切り出す価値があるか確認する。既存ADRをSnackbarの説明で置き換えない。

## 4. 実装ステップ

1. Windows側で機能用ブランチを作成し、この設計メモをレビューする。
2. MainActivityにSnackbarHostStateとSnackbarHostを用意する。各部品の役割を説明し、一時的な表示確認を行う。
3. rememberCoroutineScopeとshowSnackbarで表示する仕組みを確認する。確認用のUIやコードは残さない。
4. 登録成功の表示callbackをMainActivityとNavHostで接続し、一覧への遷移・再取得を維持して表示を確認する。
5. 編集成功・削除成功へ広げ、文言をstrings.xmlで管理する。
6. 失敗・キャンセル・再表示・連続操作と既存の操作制御を確認する。
7. journalを作成し、README、文書一覧、基本設計、要件、ロードマップと関連ADRを必要に応じて更新する。
8. 開発者のレビュー後、コミット・push・PR作成へ進む。

Snackbarの配置、表示状態、suspend関数とCoroutineの関係、callbackによる責務分担を実際のコードと結び付けて説明し、完成コードを一括で提示しない。

## 5. 確認方法

以下は確認予定であり、実施済みではない。Android Studioでのビルド・手動確認とCodexの静的レビューを区別してjournalへ記録する。自動テスト基盤は今回追加しない。

- 登録成功で一覧へ戻り、「Todoを登録しました」が表示される。
- 編集成功で詳細へ戻り、「Todoを更新しました」が表示される。一覧へ戻るだけでは同じメッセージを再表示しない。
- 削除成功で一覧へ戻り、「Todoを削除しました」が表示される。最後の1件の削除でも表示される。
- 各メッセージは短時間で消え、確認操作を要求しない。
- 単なる再コンポーズ・戻り先への再訪で同じメッセージが繰り返されない。
- 通信失敗、入力画面から保存せず戻る操作、削除確認のキャンセルでは成功メッセージが出ない。
- 続けて成功した操作の種類に対応したメッセージが表示される。
- 成功後の一覧・詳細再取得を壊していない。再取得失敗の既存のError表示・retryも維持する。
- 送信・削除中の戻る制御、失敗後の入力保持と操作復帰を壊していない。
- 確認用のボタンや仮のメッセージを実装へ残していない。

## 6. 想定される詰まりポイント

- SnackbarHostを置くだけでは表示されない。Hostと表示状態を接続し、showSnackbarで依頼する。
- showSnackbarは表示終了を待つsuspend関数である。操作元のLaunchedEffectで表示終了を待ってから戻る構成にしない。
- 操作元の画面を破棄すると、その画面のCoroutineもキャンセルされる。共通Hostに対応する表示用ScopeをMainActivityに置く。
- rememberせずSnackbarHostStateを作り直すと、再コンポーズ時に表示状態が失われる。
- stringResourceはComposableから呼ぶ。通常のcallback内へ直接書かない。
- API操作の成功、再取得要求の受付、再取得完了は別の出来事である。
- Snackbarを表示したかどうかの管理を、既存の再取得通知と兼用しない。
- 成功メッセージの実装に合わせてViewModelやAPIを再設計しない。まず現在のcallbackと共通Scaffoldを使う。
