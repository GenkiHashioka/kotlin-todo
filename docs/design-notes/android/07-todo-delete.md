# 詳細設計メモ - Android 07: 詳細画面からTodoを削除する

**バージョン**: 0.1
**作成日**: 2026-10-12
**対応Issue**: [#54](https://github.com/GenkiHashioka/kotlin-todo/issues/54)

## 1. 背景

Androidの一覧・詳細・登録・編集に続き、既存Todoを削除できるようにする。削除操作は詳細画面だけに設置し、確認ダイアログで承認された場合に通信する。

関連文書: [要件](../../requirements.md)、[基本設計](../../architecture.md)、[ADR 0006: DELETEのレスポンス](../../decisions/0006-delete-returns-200-with-body.md)、[ADR 0023: 通知から再取得する方針](../../decisions/0023-navigation-refresh-notification.md)。

## 2. スコープ

### この機能で扱うもの

- 詳細取得成功時に削除ボタンを表示する。
- 削除ボタンから確認ダイアログを開き、キャンセル・ダイアログを閉じる操作では削除しない。
- 確認後にDELETE /todos/{id}を呼ぶ。
- 削除中・削除済みの追加送信と編集操作を防ぐ。
- 削除失敗時は詳細データを保持し、共通の失敗メッセージと再操作を提供する。
- 削除成功時だけ一覧へ再取得通知を送り、詳細画面を閉じる。
- 一覧は既存のrefreshで最新データを取得する。最後のTodoを削除した場合も既存のEmpty表示を使う。

### 今回扱わないもの

一覧・編集画面からの削除、複数件削除、Undo、Backend拡張、DIライブラリ、自動テスト基盤、全面的なレイアウト変更、ui/todoのパッケージ整理。

登録・編集・削除の成功メッセージ（Snackbar / Toast / 完了画面）は[Issue #55](https://github.com/GenkiHashioka/kotlin-todo/issues/55)で扱う。今回は削除成功後に一覧へ戻って更新するところまでとし、成功フィードバックの共通化は含めない。

## 3. 設計方針

### 3.1 既存APIの利用

BackendのDELETE /todos/{id}は、成功時に200 OKと削除したTodoのレスポンスを返す。AndroidのTodoApiへDELETEを追加し、返り値は既存のTodoDtoを使う。リクエスト本文はなく、IDをURLパスに渡す。RepositoryはdeleteTodoを提供する。

ViewModelはRepositoryの処理が正常に戻ったことを成功として扱う。削除レスポンスのTodoを一覧へ手動反映せず、一覧APIを再取得する。

### 3.2 状態と責務

TodoDetailUiStateのLoading / Error / Successという構成を維持し、SuccessにisDeleting、hasDeleteError、isDeletedを追加する案とする。初期値はいずれもfalse。取得失敗のErrorと、詳細を表示し続ける削除失敗を区別する。

| 要素 | 責務 |
|---|---|
| TodoDetailScreen | 詳細・削除確認ダイアログ・削除失敗を表示し、操作をcallbackで通知する |
| TodoDetailRoute | StateFlowを監視し、削除callbackをViewModelへ接続し、成功時の副作用をNavHostへ通知する |
| TodoDetailViewModel | 既存のtodoIdを使い、削除のガード、通信、状態更新を行う |
| TodoDetailUiState | 取得状態と、取得成功後の削除状態を表す |
| TodoRepository / TodoApi | 既存DELETE APIの呼び出しを提供する |
| KotlinTodoNavHost | 一覧への再取得通知と詳細を閉じる操作を担当する |

新しいDestination、削除専用ViewModel、Factoryは作らない。詳細ViewModelは既にNavigation引数からtodoIdを取得しているため、画面のcallbackでIDを受け渡し直さない。

確認ダイアログの開閉は通信状態ではなく画面内の表示状態としてScreenに保持する案とする。確認ボタンでダイアログを閉じ、削除callbackを呼ぶ。キャンセル・dismissでは閉じるだけ。ダイアログ表示状態の再生成対応は実装時に必要性を確認する。

### 3.3 削除処理と失敗

ViewModelのdeleteTodoは通常の関数とし、viewModelScope.launchからRepositoryのsuspend関数を呼ぶ。Success以外、削除中、削除済みの場合は開始しない。通常のUIからメインスレッドで呼ぶ構成を前提に、launchより先にisDeletingをtrue、hasDeleteErrorをfalseへ更新する。

通常の例外はSuccess内のhasDeleteErrorで表し、todoを保持する。CancellationExceptionは再スローする。finallyでisDeletingをfalseへ戻す。正常に戻った場合だけisDeletedをtrueにする。

Screenでは削除中・削除済みの削除ボタンと編集ボタンを無効にする。失敗メッセージはstrings.xmlで管理する。失敗後は再び削除ボタンから確認を経て通信できるようにし、取得用retryとは目的を分ける。

### 3.4 成功後の一覧更新

RouteはSuccessのisDeletedをキーにしたLaunchedEffectで成功を受け取り、onDeletedを呼ぶ。NavHostは直前の一覧のSavedStateHandleへtodo_list_refresh_required = trueを保存してからpopBackStackする。

```text
詳細 → 削除確認 → DELETE成功
                   ↓
               isDeleted = true
                   ↓
         RouteからNavHostへonDeleted
                   ↓
      一覧へ再取得通知を保存して詳細を閉じる
                   ↓
          一覧APIの結果でStateFlowを更新
```

現在の一覧 → 詳細という経路を前提とする。削除したTodoの詳細は再取得せず閉じる。一覧の通知処理は既存のものを使い、通知の消費は再取得要求の受付であって通信完了ではない。再取得失敗は一覧のError表示とretryで扱う。

### 3.5 ADRの扱い

DELETEのレスポンス契約はADR 0006を継続する。通知から再取得する方針もADR 0023を利用し、実装後に削除を適用範囲へ追記するか確認する。詳細限定の削除と確認ダイアログ、状態項目はこの設計メモへ記録し、現段階では重複する新規ADRを作らない。

## 4. 実装ステップ

1. 削除用ブランチを作成し、Issueとこの設計メモをレビューする。成功フィードバックは別Issueとしてストックする。
2. TodoApiのDELETEとRepositoryのdeleteTodoを追加する。
3. 詳細のSuccessへ削除状態を追加し、ViewModelでガード・通信・例外処理を実装する。
4. 詳細Screenの削除ボタンと確認ダイアログ、失敗表示、通信中の操作制御を接続する。
5. Routeの成功callbackとNavHostの一覧通知・戻る操作を接続する。
6. 手動確認と理解確認を行い、journal、README、文書一覧、基本設計、要件、ロードマップ、関連ADRを必要に応じて更新する。
7. コードと文書をレビューし、開発者がコミット・push・PR作成を行う。

各ステップを小さく実装・レビューする。削除機能の完了後には、ロードマップにあるui/todoの構成整理の必要性も見直す。

## 5. 確認方法

以下は確認予定であり、実施済みではない。Android Studioでビルド・実行し、開発者の手動確認結果とCodexの静的レビューを区別してjournalへ記録する。自動テスト基盤は今回の対象外。

- 詳細取得成功時だけ削除操作ができ、取得中・取得失敗では削除できない。
- 削除ボタンを押しただけでは削除されず、キャンセル・dismissでもTodoが残る。
- 確認後に対象のTodoを削除し、一覧へ戻ってそのTodoが表示されなくなる。
- 削除中・削除済みの追加送信と編集操作を防ぐ。
- 通信失敗時も詳細を表示し続け、エラーを表示して再操作できる。
- 再試行時に前回の削除エラーを解除する。
- 別のTodoを続けて削除しても一覧通知が機能する。
- 最後のTodoを削除すると一覧がEmptyになる。
- 既存の詳細表示・編集への遷移と、登録・編集後の一覧更新を壊していない。

## 6. 想定される詰まりポイント

- 取得失敗と削除失敗を同じErrorへ変換すると、削除失敗時に詳細を失う。Success内の操作状態で扱う。
- Screenのボタン無効化だけでは追加呼び出しを防げない。ViewModelにもガードを置く。
- 削除済みのガードがないと、通信終了から遷移までに再送信できる。
- ダイアログを開く操作と削除開始を分ける。キャンセルはAPIや再取得通知を呼ばない。
- Composable本体で直接遷移せず、RouteのLaunchedEffectからcallbackで通知する。
- 削除して戻るだけでは、保持された一覧データに削除前のTodoが残る。APIの再取得でデータ自体を更新する。
- 通信中に画面を離れてCoroutineがキャンセルされても、Backendの削除を取り消せる保証はない。戻る操作を制御するかは実装時に確認する。
- Backendで削除済みだがレスポンスを受け取れない場合、再試行は404になり得る。今回は404を成功と同一視せず通常の失敗として扱い、結果不明時の整合回復は別課題とする。
