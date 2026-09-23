# 詳細設計メモ - Android 04: Todo一覧から詳細画面へ遷移する

**バージョン**: 1.0
**最終更新**: 2026-09-23
**対応Issue**: #44

## 1. 背景

現在のAndroidクライアントは、起動時にTodo一覧を取得して表示する1画面構成である。`MainActivity`が一覧用ViewModelを生成し、`TodoListRoute`を直接表示しているため、複数画面を切り替える仕組みはまだ存在しない。

Issue #44では、一覧で選択したTodoのIDを詳細画面へ渡し、既存Ktor APIの`GET /todos/{id}`からそのTodoを取得して表示する。ここでは画面を増やすことだけでなく、Navigation、画面ごとのViewModel、戻る操作、経路引数からデータを取得する流れを理解することを目的とする。

## 2. スコープ

### このPRで作るもの

- Todo一覧とTodo詳細を切り替えるNavigation Composeのナビゲーショングラフ
- 型安全な一覧・詳細のDestination
- 一覧項目を選択してTodo IDを詳細画面へ渡す処理
- `GET /todos/{id}`を呼び出すRetrofit APIとRepository処理
- 詳細画面専用のUI State、ViewModel、Route、Screen
- Loading、Success、Errorの表示と再試行
- description、dueDate、categoryが`null`の場合の表示
- 画面上の戻る操作とAndroid標準の戻る操作

### このPRで作らないもの

- Todoの登録、編集、削除
- Categoryの登録、編集、削除
- Deep Link
- Bottom Navigation
- マルチモジュール向けのNavigation構成
- HiltなどのDIライブラリ
- 日時やenum表示を整える本格的なUIモデル
- HTTPステータスごとに異なるエラーメッセージ
- 自動テスト

## 3. 画面遷移とデータフロー

### 3.1 画面遷移

```text
Todo一覧
  ↓ Todoを選択し、todoIdを渡す
Todo詳細
  ↓ 戻る
Todo一覧
```

一覧画面から詳細画面へ渡す値は`TodoDto`全体ではなく`todoId: Long`だけとする。詳細画面は受け取ったIDを使い、Repositoryから最新のTodoを取得する。

Android公式も、画面遷移では複雑なデータを渡さず、IDなど必要最小限の情報を渡してデータ層から取得することを推奨している。この構成には次の利点がある。

- Navigationが大きなオブジェクトの受け渡し方法に依存しない
- 詳細画面を開いた時点のデータをAPIから取得できる
- 詳細画面のViewModelが、必要なデータの取得責任を持てる
- 将来Deep Linkなど別経路から詳細画面を開く場合も、IDがあれば同じ処理を利用できる

### 3.2 詳細画面のデータフロー

```text
TodoListScreen
  ↓ onTodoClick(todoId)
TodoListRoute
  ↓ onTodoClick(todoId)
KotlinTodoNavHost
  ↓ navigate(TodoDetailDestination(todoId))
Navigation Back Stack
  ↓ SavedStateHandle.toRoute<TodoDetailDestination>()
TodoDetailViewModel
  ↓ getTodo(todoId)
TodoRepository
  ↓ GET /todos/{id}
TodoApi / Retrofit / OkHttp
  ↓ TodoDto
TodoDetailViewModel
  ↓ TodoDetailUiState
TodoDetailRoute
  ↓
TodoDetailScreen
```

## 4. Navigationの設計

### 4.1 Navigation Composeを採用する

現在の画面はすべてComposableであるため、`androidx.navigation:navigation-compose:2.10.1`を追加する。Navigation 3への移行を同時に扱わず、このIssueが対象とするNavigation Composeで一覧・詳細の基本的な遷移を学ぶ。

依存関係は既存と同様にVersion Catalogへ定義する。

### 4.2 型安全なDestinationを定義する

文字列で`"todo/{id}"`のようなrouteを組み立てず、Kotlin Serializationを使った型安全なrouteを採用する。プロジェクトにはSerialization pluginがすでに導入されている。

```kotlin
@Serializable
data object TodoListDestination

@Serializable
data class TodoDetailDestination(
    val todoId: Long,
)
```

引数を持たない一覧は`data object`、Todo IDを持つ詳細は`data class`とする。`todoId`の型がroute定義に含まれるため、文字列routeのタイプミスや手動の型変換を減らせる。

### 4.3 `NavController`をScreenへ渡さない

Navigationを実行するのは、ナビゲーショングラフを定義する`KotlinTodoNavHost`とする。`TodoListScreen`は次のようなイベントだけを受け取る。

```kotlin
onTodoClick: (Long) -> Unit
```

`TodoDetailScreen`も`NavController`ではなく、次のイベントを受け取る。

```kotlin
onBack: () -> Unit
```

Screenは「クリックされた」「戻るが押された」というUIイベントを通知するだけで、遷移先やBack Stackの存在を知らない。この分離により、ScreenのPreview、再利用、将来のUIテストがしやすくなる。

### 4.4 ViewModelを画面のBack Stack Entryに紐づける

現在は`MainActivity`で一覧ViewModelを生成している。Navigation導入後は、各`composable` Destinationの中で`viewModel(factory = ...)`を呼び、そのDestinationの`NavBackStackEntry`をViewModelのOwnerとする。

これにより、一覧ViewModelと詳細ViewModelの生存期間がそれぞれの画面に対応する。詳細画面をBack Stackから取り除くと詳細ViewModelも破棄され、その`viewModelScope`で実行中のCoroutineもキャンセルされる。

RepositoryとViewModel Factoryは、引き続き`MainActivity`を生成元とする。現段階ではDIライブラリを追加しない。

## 5. 詳細ViewModelへのTodo IDの受け渡し

`TodoDetailViewModel`は`SavedStateHandle`から型安全なrouteを復元する。

```kotlin
val destination = savedStateHandle.toRoute<TodoDetailDestination>()
```

その`destination.todoId`を詳細取得に使う。`SavedStateHandle`はNavigation引数をViewModelから読めるようにし、プロセス再生成時にもNavigationが復元した引数を同じ入口から受け取れる。

手動のViewModel Factoryでは`CreationExtras.createSavedStateHandle()`を利用し、Repositoryと`SavedStateHandle`を詳細ViewModelへ渡す。FactoryにTodo IDを直接渡す方法もあるが、今回はNavigation引数をViewModelで受ける公式の構成を学ぶため`SavedStateHandle.toRoute()`を採用する。

## 6. APIとRepository

### 6.1 Retrofit API

既存の`TodoApi`へ詳細取得を追加する。

```kotlin
@GET("todos/{id}")
suspend fun getTodo(
    @Path("id") todoId: Long,
): TodoDto
```

`@Path("id")`は`{id}`の部分へ`todoId`を埋め込む。ベースURLが`http://<WSL2_IP>:8080/`、`todoId`が`3`なら、リクエスト先は`http://<WSL2_IP>:8080/todos/3`になる。

### 6.2 Repository

`TodoRepository`へ次の責務を追加する。

```text
getTodo(todoId)
  ↓
TodoApi.getTodo(todoId)
```

ViewModelはRetrofitのannotationやURL構造を知らず、`todoRepository.getTodo(todoId)`としてデータ取得を依頼する。キャッシュや取得元の切り替えが必要になった場合も、その判断をRepository側へ置ける。

## 7. 詳細画面の状態

詳細画面用に`TodoDetailUiState`を定義する。

```text
Loading
  APIから取得中

Success(todo)
  1件のTodoを取得できた

Error
  通信失敗、404、変換失敗などで表示できない
```

一覧画面の`Empty`は「0件の一覧」という正常な取得結果を表す。詳細取得は1件のリソースを要求するため、該当データがなければ成功時の空状態ではなくErrorとして扱い、`Empty`は定義しない。

`TodoDetailViewModel`は生成時に詳細取得を開始する。再試行時は同じ`todoId`で再取得し、最初にLoadingへ戻す。例外処理は一覧ViewModelと同じく、`CancellationException`を再throwしてCoroutineのキャンセルを妨げない。

このIssueではHTTPステータス別のメッセージまでは分けず、ユーザーが再試行できる共通のError表示とする。

## 8. 詳細画面の表示

`TodoDetailScreen`は`TodoDetailUiState`に応じて表示を切り替える。Successでは次を表示する。

- title
- description
- dueDate
- priority
- status
- category name
- createdAt
- updatedAt

nullableな項目は次のように扱う。

| 項目 | `null`の場合 |
|---|---|
| description | `未設定` |
| dueDate | `未設定` |
| category | `未設定` |

APIから受け取った日時文字列とenum値は、まず値を正しく表示するところまでを対象とする。日時フォーマットや日本語の表示名への変換は、必要性を確認して別の改善として扱う。nullable値の置き換えはUI表示の都合なので、DTOの値自体は変更しない。

戻る操作には、追加のアイコン依存関係を増やさずMaterial 3の`Button`を使う。Android標準のBack操作は`NavController`のBack Stackによって処理される。

戻るボタンは画面上部に置き、その下の表示領域へ`weight(1f)`を指定する。LoadingとErrorは残りの領域で中央に表示し、Successは項目が画面より長い場合にスクロールできるようにする。

## 9. ファイルと責務

既存構成を大きく移動せず、必要になったファイルだけを追加する。

```text
ui/navigation/
  TodoDestinations.kt       一覧・詳細の型安全なroute
  KotlinTodoNavHost.kt      NavController、NavHost、画面遷移

ui/todo/
  TodoListRoute.kt          一覧ViewModelとScreenの接続、クリックイベントの受け渡し
  TodoListScreen.kt         一覧表示とクリック通知
  TodoDetailUiState.kt      詳細画面の状態
  TodoDetailViewModel.kt    Todo IDによる取得と状態更新
  TodoDetailViewModelFactory.kt
  TodoDetailRoute.kt        詳細ViewModelとScreenの接続
  TodoDetailScreen.kt       詳細表示とUIイベント通知
```

一覧と詳細がさらに大きくなった時点で`ui/todo/list`と`ui/todo/detail`への分割を検討する。このIssueでは既存ファイルの移動を同時に行わない。

`Route`と`Screen`の責務は次のように分ける。

| 層 | 知ってよいもの | 責務 |
|---|---|---|
| `KotlinTodoNavHost` | `NavController`、Destination、ViewModel Factory | 画面登録、遷移、戻る操作、画面ごとのViewModel生成 |
| `Route` | ViewModel、UI State、Screen | 状態の監視、ViewModel処理とUIイベントの接続 |
| `Screen` | UI State、callback | 状態に応じた描画、ユーザー操作の通知 |
| ViewModel | Repository、Todo ID | データ取得、画面状態の更新 |
| Repository | `TodoApi` | データ取得方法の提供 |

## 10. 実装ステップ

NavigationとAPI取得を一度に作らず、問題を切り分けられる順序で進める。

1. Navigation Compose依存関係と型安全なDestinationを追加する
2. `KotlinTodoNavHost`と仮の詳細画面を作り、一覧からIDを渡して戻れることを確認する
3. `TodoApi`と`TodoRepository`へ1件取得処理を追加する
4. `TodoDetailUiState`、`TodoDetailViewModel`、Factoryを作る
5. `TodoDetailRoute`と`TodoDetailScreen`を作り、取得結果とError・再試行を表示する
6. `MainActivity`をNavHostへ接続し、画面ごとのViewModel生成を確認する
7. Emulatorで一覧、詳細取得、戻る、通信失敗、再試行を通して確認する

仮の詳細画面でNavigationだけを先に確認することで、遷移できない問題とAPI取得に失敗する問題を分けて調査できる。

## 11. 確認方法

### ビルド

```powershell
cd android
.\gradlew.bat :app:assembleDebug
```

### Emulator

- 一覧でTodoを選ぶと詳細画面へ遷移する
- 選んだTodoのIDで`GET /todos/{id}`が呼ばれる
- 選んだTodoの各項目が表示される
- `null`のdescription、dueDate、categoryが`未設定`と表示される
- 画面上の戻る操作で一覧へ戻る
- Android標準の戻る操作で一覧へ戻る
- Backend停止中はErrorが表示される
- Backend再起動後、再試行で詳細が表示される
- 一覧へ戻った際、一覧画面が操作できる

## 12. 想定される詰まりポイント

- `TodoDto`全体をNavigation引数にせず、Todo IDだけを渡す
- `NavController`をScreenへ渡さず、callbackでUIイベントを上位へ返す
- `@Serializable`を`kotlinx.serialization.Serializable`からimportする
- `SavedStateHandle.toRoute()`にはNavigationの型安全なroute定義が必要になる
- ViewModelを`MainActivity`で先に生成せず、各DestinationのBack Stack Entryに紐づける
- Factoryで`SavedStateHandle`を作る際は、ViewModelのCreation Extrasを利用する
- 詳細画面には一覧の`Empty`をそのまま流用しない
- `catch (Exception)`より前に`CancellationException`を再throwする
- 画面を戻したあとに不要な詳細ViewModelが残らないことを確認する
- WSL2再起動後にAPIへ接続できない場合は、Issue #43で用意したGradle User HomeのベースURLを更新してrebuildする

## 13. 参考資料

- [Android Developers: Type safety in Kotlin DSL and Navigation Compose](https://developer.android.com/guide/navigation/design/type-safety)
- [Android Developers: Migrate Jetpack Navigation to Navigation Compose](https://developer.android.com/develop/ui/compose/migrate/migration-scenarios/navigation)
- [Android Developers: Navigation release notes](https://developer.android.com/jetpack/androidx/releases/navigation)
