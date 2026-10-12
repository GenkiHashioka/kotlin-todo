# 0023 - 登録・編集成功後はNavigationの通知からAPIを再取得する

**ステータス**: 採用
**日付**: 2026-10-12

## Context（背景・何を解決したいか）

登録や編集に成功して画面を戻っても、戻り先のBackStackEntryに紐づくViewModelは保持され、initの取得は再実行されない。再コンポーズだけではBackendの新しい値を取得できない。登録は一覧、編集は詳細と一覧の両方を更新する必要がある。

画面遷移の責務をViewModelへ持ち込まず、各画面が自身の取得処理で最新データを表示できる構成にする。

## Decision（何を決めたか）

登録・編集の成功状態をRouteのLaunchedEffectからNavHostへcallbackで通知する。NavHostは対象BackStackEntryのSavedStateHandleにBooleanの再取得要求を保存してからpopBackStackする。

- 登録成功: 直前の一覧へtodo_list_refresh_requiredを保存する。
- 編集成功: 直前の詳細へtodo_detail_refresh_required、Back Stackの一覧へtodo_list_refresh_requiredを保存する。
- 各画面はgetStateFlowとcollectAsStateWithLifecycleで通知を監視し、trueならrefreshを呼び、falseへ戻す。
- falseへ戻すのは要求の受付を意味する。通信完了を待たずに消費し、失敗は各画面の既存のError表示とretryで扱う。
- 再取得の結果を各ViewModelのStateFlowへ保存して表示を更新する。

登録は一覧から、編集は一覧・詳細から開く現在の経路を前提とする。ViewModelにはNavControllerや別画面のViewModelを渡さない。

## Consequences（この決定によって何が得られ、何を犠牲にしたか）

既存の取得・失敗表示・再試行を再利用でき、更新後のDTOを各画面へ手動で反映する処理やRepositoryの共有キャッシュを追加せずに済む。

一方、保存後に追加通信が発生し、再取得に失敗する可能性がある。Booleanは要求の集約であり、イベントの回数や順序を保持するものではない。通知の受付後に再取得が中断されても自動再送を保証しない。画面経路を増やす場合は通知先のBackStackEntryの前提を見直す。

### 代替案

更新後のDTOを各画面へ渡してStateFlowへ反映する方法は、再取得を減らせるが、一覧への追加・置換や詳細への反映を各画面で整合させる必要がある。現在はAPIから再取得する方針を使う。

Repositoryの共有キャッシュやFlowを各画面で監視する方法は、共有状態の管理が必要になる。現状の規模では既存の取得処理で対応できるため導入しない。画面や更新経路が増え、通知の維持が難しくなった時点で再検討する。

## 関連

- [Android 05: Todo登録の設計メモ](../design-notes/android/05-todo-create.md)
- [Android 06: Todo編集の設計メモ](../design-notes/android/06-todo-edit.md)
- [Android 06: 学習ジャーナル](../journal/android/06-todo-edit.md)
- [Architecture](../architecture.md)
