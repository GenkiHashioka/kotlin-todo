# Android 04 - Todo一覧から詳細画面へ遷移する

**ステータス**: 実装・手動確認完了（PR前）
**記録日**: 2026-09-23
**対応Issue**: #44
**設計メモ**: [Android 04: Todo一覧から詳細画面へ遷移する](../../design-notes/android/04-todo-detail-navigation.md)

## 目的

一覧で選んだTodoの詳細を既存Ktor APIから取得し、別画面に表示する。Navigationによる画面遷移、画面ごとのViewModel、API取得結果からComposeの表示までを自分で説明できるようにする。

## 作成したもの

- 型安全な一覧・詳細Destinationと`KotlinTodoNavHost`
- 一覧項目のクリックから`todoId`だけを詳細画面へ渡す処理
- Retrofitの`GET /todos/{id}`とRepositoryの1件取得処理
- 詳細用の`TodoDetailUiState`、ViewModel、Factory、Route、Screen
- Loading、Success、Error、再試行、戻る操作、nullable項目の`未設定`表示

## 学んだこと

### 画面遷移で渡すものはTodoのID

```text
TodoListScreenのクリック
  → TodoListRouteのonTodoClick
  → KotlinTodoNavHostのnavigate(TodoDetailDestination(todoId))
  → 詳細DestinationのBack Stack Entry
  → SavedStateHandle.toRoute<TodoDetailDestination>().todoId
```

一覧で表示中の`TodoDto`全体を渡さず、詳細画面のViewModelがIDを使ってAPIから取得する。詳細画面は一覧の表示データに依存せず、選択したIDの現在のデータを取得できる。

`composable<T> { backStackEntry -> ... }`の`backStackEntry`は、Navigation Composeがlambdaへ渡す引数である。Kotlinの暗黙の引数`it`でも受け取れるが、何を指すか分かる名前にした。

### 詳細画面の状態を分ける

```text
TodoDetailViewModel
  → TodoRepository.getTodo(todoId)
  → TodoApi / Retrofit / Ktor API
  → TodoDetailUiState（Loading / Success(todo) / Error）
  → TodoDetailRoute
  → TodoDetailScreen
```

1件取得では、該当Todoが存在しない場合にBackendが404を返し、Retrofitから例外が発生する。このため一覧の「取得成功・0件」を表す`Empty`は詳細画面に設けず、今回は共通の`Error`として表示する。

`when`の`is TodoDetailUiState.Success`は、値を持つクラスの型を判定し、その`todo`へアクセスするために使う。`Loading`と`Error`は`data object`なので、オブジェクトそのものと比較できる。`sealed interface`により、状態を追加した際に表示側の分岐漏れをコンパイル時に検出しやすい。

### ViewModelとComposeの生存期間

各Destination内で生成したViewModelは、そのBack Stack Entryに紐づく。詳細画面をBack Stackから外すと詳細ViewModelも破棄され、`viewModelScope`のCoroutineはキャンセルされる。`CancellationException`を通常の`Exception`と同じErrorへ変換しないよう、先に再throwする。

`collectAsStateWithLifecycle()`は常時収集する仕組みではない。画面のLifecycleが既定の`STARTED`以上の間にFlowを収集し、下回ると収集を止める。画面へ戻って収集が再開すると、`StateFlow`の最新値から表示を更新する。Flowの収集と、ViewModelが開始したAPI通信のCoroutineは別の処理である。

### レイアウトの高さとスクロール

戻るボタンの下の`Box`へ`weight(1f)`を指定すると、親`Column`の残りの高さを使う。指定しなければ内容に必要な高さに縮み、`Alignment.Center`はその小さい`Box`の中央にしかならない。Successの内容は`verticalScroll`でスクロール可能にし、項目が増えても確認できるようにした。

## 動作確認

- AndroidのSyncと`:app:assembleDebug`が成功
- Emulatorで一覧のTodoを押し、選んだIDの詳細が表示されることを確認
- 詳細の各項目とnullable項目の`未設定`表示を確認
- 画面上の戻る操作とAndroid標準の戻る操作を確認
- Backend停止時のError表示と、起動後の再試行を確認
- レイアウト修正後も画面表示を確認

開発中、Emulatorで`System UI isn't responding`が頻発した。AVDのRAMを4GBに変更した後、ユーザー環境では解消した。これはアプリのビルド成功とは別に、Emulator側の動作も確認する必要があると分かった例である。

## 設計メモとの差分と現在の制約

戻る操作には設計時に想定した`TextButton`ではなく`Button`を使用した。詳細画面では、戻るボタンを上部に残し、状態表示を残りの領域へ配置した。Navigationで渡す値、API取得、状態の責務分担は設計どおり。

日時とenumはAPIの値をそのまま表示する。404専用のメッセージ、DIライブラリ、Deep Link、自動テストはこのIssueの対象外とし、必要になった段階で検討する。

## 理解度確認

- [x] 一覧のクリックから詳細ViewModelへTodo IDが渡る経路
- [x] `SavedStateHandle`でNavigation引数を読む理由
- [x] Repositoryを通して詳細を再取得する理由
- [x] `Success(todo)`からComposeの表示までの流れ
- [x] `collectAsStateWithLifecycle()`が収集する期間と`StateFlow`の最新値
- [x] 詳細画面に`Empty`を設けない理由
- [x] `CancellationException`を再throwする理由
- [x] `weight(1f)`と`Alignment.Center`がレイアウトへ与える影響

各項目について、実装・レビュー・対話を通じて自分の言葉で説明できることを確認した。
