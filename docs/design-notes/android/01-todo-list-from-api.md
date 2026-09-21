# 詳細設計メモ - Android 01: APIからTodo一覧を表示する

**バージョン**: 1.0
**最終更新**: 2026-09-21
**対応Issue**: #41

> このdesign noteは、Android Track開始時に設計相談を会話で進め、実装完了後に合意内容を文書へ戻したもの。次の機能からは実装前に作成する。

## 1. 背景

KtorバックエンドにはTodo CRUD APIが存在するが、利用するクライアントはcurlとSwagger UIだけだった。Kotlin / Androidの設計と新規実装を学ぶため、Jetpack ComposeによるAndroidクライアントを追加する。

最初の機能では、画面からAPIまでの一本のデータフローを最小構成で通す。完成度の高いArchitectureを先に作るのではなく、実際の責務が発生した段階でクラスとパッケージを追加する。

## 2. スコープ

### このPRで作るもの

- `android/`のAndroidプロジェクト
- Ktor APIの`GET /todos`に対応するDTO
- kotlinx.serializationによるJSON変換
- RetrofitによるTodo一覧取得
- Repository、ViewModel、StateFlow、UI State
- Jetpack Composeの`LazyColumn`によるタイトル一覧
- Android EmulatorからWSL2上のKtor APIへのdebug接続

### このPRで作らないもの

- Todo詳細、作成、編集、削除
- Loading / Error / Empty State
- Navigation
- DIライブラリ
- Androidの自動テスト
- API DTOとUIモデルの分離
- 本番環境用のAPI URL管理
- Backendの機能拡張

## 3. 設計方針

### 3.1 データフロー

```text
TodoListScreen
    ↑ TodoListUiState
TodoListRoute
    ↑ StateFlow
TodoListViewModel
    ↓
TodoRepository
    ↓
TodoApi
    ↓
Retrofit / OkHttp
    ↓ HTTP GET /todos
Ktor API
```

Composeは読み取り専用の`StateFlow`を監視し、画面状態を直接変更しない。ViewModelだけが内部の`MutableStateFlow`を更新する。

### 3.2 HTTP Client

**採用**: Retrofit 3 + OkHttp + kotlinx.serialization converter

理由:

- Kotlin interfaceとHTTPアノテーションの対応が読みやすい
- Android開発で広く使われる構成を経験できる
- `suspend fun`を直接定義できる
- Ktor Serverを使っていても、クライアントまでKtor Clientに揃える必然性はない

**代替案**: Ktor Client

Kotlinで統一でき、将来Kotlin Multiplatformを選ぶ場合には有力。ただし現時点の目標はAndroidであり、利用するプラットフォームも1つなので採用しない。

### 3.3 DTO

APIレスポンスのフィールド名、null許容、enum定数をそのまま表す。

- `description`, `dueDate`, `category`はnullable
- `priority`, `status`はAPIの文字列と同名のenum
- 日付と日時は初回実装では`String`で受け取る
- 未知のフィールドは`ignoreUnknownKeys = true`で無視する

初回は`TodoListUiState`も`TodoDto`を保持する。表示用の変換要件が生じた時点でUIモデルを追加する。

### 3.4 Repository

`TodoRepository`はコンストラクタで`TodoApi`を受け取る。Repository内から`ApiClient`を直接参照しない。

```text
MainActivityが具体的な依存関係を組み立てる
    ↓
TodoRepository(TodoApi)
    ↓
TodoListViewModel(TodoRepository)
```

初回はinterfaceと実装クラスへ分けない。Fakeへの差し替えが必要になるテスト導入時に、具体的な問題を見て再判断する。

### 3.5 UI State

初回の状態はTodo一覧だけを持つ。

```kotlin
data class TodoListUiState(
    val todos: List<TodoDto> = emptyList(),
)
```

初期状態と取得結果0件を区別せず、通信失敗も表現しない。Loading / Error / Empty Stateは後続Issueで設計する。

### 3.6 ViewModel

- `MutableStateFlow`は`private`にする
- 外部には`asStateFlow()`で読み取り専用の`StateFlow`を公開する
- `init`から一覧取得を開始する
- `viewModelScope`でRepositoryを呼ぶ
- 取得結果で`TodoListUiState`を更新する

コンストラクタ引数を持つため、AndroidXの`viewModelFactory` DSLでFactoryを作る。

### 3.7 Compose

- `TodoListRoute`はStateFlowを`collectAsStateWithLifecycle()`で監視する
- `TodoListScreen`は`TodoListUiState`だけを受け取る
- `LazyColumn`の各要素はTodoの`id`を`key`にする
- 初回はタイトルだけを`Text`で表示する

RouteとScreenを分け、状態管理と描画を別々に確認できるようにする。

### 3.8 DI

DIライブラリは導入しない。`MainActivity`をcomposition rootとして、`ApiClient → TodoRepository → ViewModelFactory`を手動で組み立てる。依存関係が増え、生成やスコープ管理が問題になった時点でDI導入を検討する。

## 4. 実装ステップ

1. Android StudioでEmpty Activityプロジェクトを`android/`に作成する
2. Ktor APIのJSONを確認し、DTOとenumを定義する
3. kotlinx.serializationとRetrofitの依存関係を追加する
4. `TodoApi`と`ApiClient`を作成する
5. `TodoRepository`を作成する
6. `TodoListUiState`と`TodoListViewModel`を作成する
7. ViewModel Factoryを作成する
8. `TodoListScreen`と`TodoListRoute`を作成する
9. `MainActivity`で依存関係を組み立てる
10. EmulatorでTodoタイトルが表示されることを確認する

## 5. 確認方法

### Backend

```bash
docker compose up -d postgres
cd backend
./gradlew run
```

```powershell
curl.exe -s http://localhost:8080/todos
```

### Android

```powershell
cd android
.\gradlew.bat :app:assembleDebug
```

- Android Emulatorでアプリが起動する
- APIに存在するTodoタイトルが`LazyColumn`に表示される
- `GET /todos`がKtorログに200として記録される

## 6. 想定される詰まりポイント

- APIレスポンスのnullableフィールドを非nullで定義するとJSON変換に失敗する
- `@Serializable`だけでなくcompiler pluginとJSON runtimeの両方が必要
- Retrofit converterは依存関係へ追加するだけでなくBuilderへ登録する必要がある
- `TodoListViewModel`の`loadTodos()`を呼ばなければ、ビルドは成功しても通信は始まらない
- APIエラーを処理しないため、Backend停止時はアプリが終了する
- Windows版GradleはWSLのUNCパス上でファイルロックに失敗する
- Linux版Android StudioをWSL2へ導入する場合、Windows上のEmulatorとのADB接続やWSLg、GPU・仮想化をまたぐ構成の確認が必要になる
- Android EmulatorからWindowsの`localhost`とWSL2の`localhost`は同じ経路ではない
- WSL2のIPは再起動後に変わる可能性がある
