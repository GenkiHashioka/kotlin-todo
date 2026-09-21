# Android 01 - APIからTodo一覧を表示する

**ステータス**: 完了
**完了日**: 2026-09-21
**対応Issue**: #41
**設計メモ**: [Android 01: APIからTodo一覧を表示する](../../design-notes/android/01-todo-list-from-api.md)

## 目的

既存Ktor APIを再利用し、AndroidアプリでTodo一覧を表示する。完成コードを一度に作るのではなく、次のデータフローを一段ずつ実装し、それぞれの責務を説明できる状態を目指した。

```text
Compose
→ ViewModel
→ StateFlow / UI State
→ Repository
→ Retrofit
→ Ktor API
```

## 作成したもの

- `android/`のAndroidプロジェクト
- APIレスポンスに対応する`TodoDto`, `CategoryDto`, enum
- kotlinx.serializationを登録したRetrofit Client
- `GET /todos`を表す`TodoApi`
- `TodoRepository`
- `TodoListUiState`と`TodoListViewModel`
- `TodoListViewModelFactory`
- `TodoListRoute`と`TodoListScreen`
- debugビルド用のHTTP通信許可

## 学んだこと

### Androidアプリの入口とCompose

`app`はAndroidアプリを構成するモジュールであり、`MainActivity`はAndroid OSとComposeをつなぐ入口になる。`setContent`の中にComposableのツリーを置くことで画面を構築する。

Composeは画面の見た目を宣言し、主要な画面状態はViewModelから受け取る。状態が変わると必要なComposableが再評価される。

### API契約とDTO

DTOは「画面で使いたい形」ではなく、「APIが返すJSONの形」に対応する。`curl.exe`で実レスポンスを確認し、null許容とenumの文字列を合わせた。

```text
description: nullになり得る → String?
dueDate: nullになり得る    → String?
category: nullになり得る   → CategoryDto?
priority: "HIGH"           → Priority.HIGH
```

コメントも型と同じ契約になる。`status`をnullableと説明したKDocは実装と矛盾しており、レビューで修正した。KDocにはコードの言い換えより、nullの意味や日付形式など型だけでは分からない情報を書く。

### kotlinx.serializationの2つの役割

- serialization compiler plugin: `@Serializable`から変換用コードを生成する
- `kotlinx-serialization-json`: 実行時にJSONとKotlinオブジェクトを変換する

両方が必要であり、Retrofitではさらに`asConverterFactory()`をBuilderへ登録する必要がある。

Version Catalogのinline tableを複数行で書いたところTOMLのsyntax errorになった。`{ ... }`形式は1行で記述する。

### Retrofit

`TodoApi`は実装クラスではなくHTTP通信の契約である。

```kotlin
@GET("todos")
suspend fun getTodos(): List<TodoDto>
```

`Retrofit.create(TodoApi::class.java)`が実行時に実装を生成する。Retrofitはアノテーション、戻り値、ベースURLを読み、OkHttpで通信してconverterでJSONを変換する。

### Repositoryと依存性注入

RepositoryはViewModelからHTTP Clientの詳細を隠す境界になる。初回はAPI呼び出しを委譲するだけだが、将来はキャッシュ、データ変換、複数data sourceの調整を置ける。

`TodoRepository`は`ApiClient`を内部で取得せず、コンストラクタで`TodoApi`を受け取る。この形により、依存関係の生成場所が呼び出し側に移り、将来差し替えやすくなる。

### ViewModel、UiState、StateFlow

役割を次のように整理した。

- `TodoListUiState`: ある時点の画面情報
- `MutableStateFlow`: ViewModel内部で更新できる状態の入れ物
- `StateFlow`: Composeへ公開する読み取り専用の状態
- `TodoListViewModel`: Repositoryへ取得を指示し、UiStateを更新する
- Compose: StateFlowを監視して描画する

```text
Repositoryがデータを取得する
    ↓
ViewModelが_uiStateを更新する
    ↓
ComposeがuiStateを監視して再描画する
```

`loadTodos()`を実装しただけでは処理は開始されない。`init { loadTodos() }`が抜けていたため、ビルドは成功してもAPI通信が行われない状態になっていた。ビルド成功と実行時の振る舞いは別に確認する必要がある。

### RouteとScreen

`TodoListRoute`はViewModelを知り、`collectAsStateWithLifecycle()`で状態を監視する。`TodoListScreen`はViewModelを知らず、渡された`TodoListUiState`を描画する。

この分離により、状態の取得と見た目を別々に考えられる。`LazyColumn`ではTodoの`id`を`key`にし、Composeが項目を安定して識別できるようにした。

### ViewModel Factory

`TodoListViewModel`は`TodoRepository`をコンストラクタで受け取るため、引数なしではAndroidが生成できない。`viewModelFactory`の`initializer`に生成方法を登録し、`viewModel(factory = ...)`からViewModelProviderを通して取得した。

単にコンストラクタを呼ぶのではなくViewModelProviderを使うことで、再composition時に既存インスタンスが再利用され、ActivityのLifecycleに紐づく。

## 開発環境で詰まったこと

### Windows版Android StudioとWSLのUNCパス

Androidプロジェクトを`\\wsl.localhost\Ubuntu\...`へ作成しようとすると、書き込み不可やGradleのファイルロックエラーが発生した。Windows版GradleとWSLファイルシステムの組み合わせが原因だった。

同じGitHubリポジトリを次の2箇所へcloneした。

```text
WSL2    ~/projects/kotlin-todo                    Backend用
Windows C:\Users\gensh\projects\kotlin-todo      Android用
```

物理的な作業場所は分かれるが、GitHub上では同じモノレポとして管理できる。

Linux版Android StudioをWSL2へ導入する案も検討した。AndroidプロジェクトとBackendを同じファイルシステムで扱えるが、今回はWindows上のAndroid Emulatorを使用するため、WSLgによるIDE表示、Windows側EmulatorとのADB接続、GPU・仮想化をまたぐ構成も確認する必要がある。現在の学習対象をAndroidアプリの設計・実装へ集中するため、安定動作を確認できたWindows版Android StudioとWindows側cloneを採用した。

したがって、Linux版Android Studioでは解決できないという結論ではない。環境構築の選択肢を増やすよりも、今回の目的に必要な構成を選んだ。

### Android EmulatorからWSL2への接続

Windows PowerShellの`curl.exe http://localhost:8080/todos`は成功したが、Emulatorから`10.0.2.2:8080`へ接続すると`ECONNREFUSED`になった。`10.0.2.2`はWindowsホストを指すが、WindowsのlocalhostからWSL2への転送と同じ経路ではなかった。

ADB reverseも試したが、この環境では接続後に`unexpected end of stream`で切断された。最終的にWSL2のIPを確認し、Emulatorから直接接続した。

```powershell
wsl -d Ubuntu -- hostname -I
```

このIPはWSL2再起動後に変わり得る。現在は暫定的に`ApiClient.kt`へ設定しており、外部設定化を後続Issueで扱う。

### 複数のEmulator

2台のEmulatorが同時に起動し、ADB reverseを設定した端末とAndroid Studioが起動対象にした端末が異なっていた。`adb devices -l`で対象を確認し、不要なEmulatorを停止する必要がある。

### 未処理の通信例外

Backend停止中や接続先が誤っている状態で、次の例外によりアプリが終了した。

```text
java.net.ConnectException: Failed to connect
```

現在の`TodoListUiState`にはError Stateがなく、ViewModelも例外を処理していないためである。これは今回のスコープ外として認識したうえで、後続IssueでUIへエラーを伝える設計を追加する。

## design noteとの差分

設計相談は実装前に行ったが、初回はdesign noteとして保存していなかった。このjournalと同時に、会話で合意した設計をdesign noteへ戻した。

機能面では予定した最小構成で実装した。開発環境については、当初想定した`10.0.2.2`ではWSL2上のAPIへ到達できず、WSL2のIPへ直接接続する方式へ変更した。

## 現在の制約

- Loading / Error / Emptyを区別できない
- Backend停止時に通信例外でアプリが終了する
- WSL2のIPをソースコードへ直接記述している
- `TodoDto`をUI Stateで直接使用している
- Androidの自動テストがない
- DIの組み立てが`MainActivity`にある

これらは失敗ではなく、最初の縦断的なデータフローを小さく完成させるために意図的に残した範囲である。具体的な問題が発生した順に改善する。

## 次の機能へ進む前の理解度確認

Android 01で扱った概念を、コードを見ながら一つずつ棚卸しした。対話を通して説明できることを確認した項目をチェック済みとする。

- [x] `MainActivity`と`setContent`の役割
- [x] DTOのnull許容をAPIレスポンスに合わせる理由
- [x] Retrofitが`TodoApi`の実装を生成する仕組み
- [x] RepositoryをViewModelとHTTP Clientの間に置く理由
- [x] `_uiState`と`uiState`を分ける理由
- [x] `TodoListRoute`と`TodoListScreen`の責務
- [x] `viewModelScope`を使う理由
- [x] `collectAsStateWithLifecycle()`を使う理由
- [x] EmulatorからWSL2へ接続するときのネットワーク経路

Issue #41で構築したデータフローを説明できることを対話で確認した。`viewModelScope`によるキャンセルの詳細と、Emulator・Windows・WSL2間のネットワーク構成は、今後の実装でも継続して理解を深める。
