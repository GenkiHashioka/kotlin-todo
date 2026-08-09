# 0014 - 依存注入は手動 DI で行い、Koin を採用しない

**ステータス**: 採用
**日付**: 2026-08-10

## Context（背景・何を解決したいか）

Spring Boot の頃は「DI コンテナに任せる」のがデフォルトだった。`@Autowired` や constructor injection で「Spring が裏でよろしくやってくれる」世界。Ktor に移行するにあたり、DI をどうするかを決める必要がある。

Ktor 本体には **DI 機構が組み込まれていない**（Spring と大きく違う点の 1 つ）。開発者が自分で選ぶ：

- 手動 DI: `main` 関数や `Application.module()` 内で `val repo = XxxRepo()` → `val service = XxxService(repo)` の順に new して繋げる
- Koin: Kotlin コミュニティで人気の軽量 DI ライブラリ、DSL で依存グラフを宣言
- Kodein: Koin と似た軽量 DI ライブラリ
- Google Guice: Java 界の老舗、Kotlin 対応もあるが Kotlin native ではない

学習プロジェクトとしてどれを選ぶかを、Ktor 骨組み段階（Phase 4.7）で決めておくと、Phase 4.9 で CRUD 実装が入る時に迷わなくて済む。

## Decision（何を決めたか）

**手動 DI** を採用する。`Application.module()` 内で依存グラフを明示的に組み立てる方式。

Phase 4.9 で予想される具体的コード例（イメージ、実装は Phase 4.9 で本人が書く）:

```kotlin
fun Application.module() {
    // Infrastructure
    val database = DatabaseFactory.init()

    // Repositories
    val userRepo = UserRepositoryImpl(database)
    val categoryRepo = CategoryRepositoryImpl(database)
    val todoRepo = TodoRepositoryImpl(database)

    // Services
    val todoService = TodoService(userRepo, categoryRepo, todoRepo)

    // Routing (Service を渡す)
    routing {
        todoRoutes(todoService)
    }
}
```

Koin や他の DI ライブラリの導入は将来のスコープ拡大時（Phase 6 の認証、あるいはそれ以降）で必要性が出た時に検討する。今は必要性が薄い（クラス数が少ない）。

## Consequences（この決定によって何が得られ、何を犠牲にしたか）

### 得られるもの

- **依存関係グラフが明示的に見える**: `Application.module()` を読めば「何が何に依存しているか」が上から下に読める。Koin なら DSL に散らばる、Spring なら annotation 経由の暗黙依存になる
- **リフレクションや魔法が無い**: 実行時にコンテナが「よしなに」bean を作るのではなく、コンパイル時に依存が確定する。デバッガでステップ実行しても分かりやすい
- **フレームワークロックインが無い**: 依存グラフが素の Kotlin コードなので、将来別のフレームワークに乗り換えても意味論は変わらない
- **Kotlin native な書き心地**: Spring DI や Koin と違い、「言語外の作法」を覚えなくていい。関数呼び出しとコンストラクタ呼び出しだけの世界
- **学習として、DI の本質（依存を上から与える）を体感できる**: DI コンテナに慣れすぎると「なぜコンストラクタで受け取るのか」の感覚が薄れる。手動で組み立てる経験は概念理解に効く

### 犠牲にするもの

- **プロジェクトが大きくなると boilerplate が増える**: クラス数が数十を超えてくると `Application.module()` が長くなる。適当な粒度で関数分割していく必要がある（`configureRepositories()`, `configureServices()` のように）
- **スコープ管理を自前で書く必要**: Spring の singleton / prototype / request scope のような概念は自前で意識する。今のところは全部 singleton（`val` で 1 回 new）で十分だが、将来 request-scoped が必要になったら関数の中で毎回 new する形になる
- **テストで依存を差し替える工夫が必要**: Spring だと `@MockBean` で楽に mock 差し替えできる。手動 DI だと「テスト用の `Application.module()` を別に書く」または「Service を interface 経由にして手動で mock を注入」といった工夫が要る（Phase 4.11 で扱う予定）
- **循環依存に気付きにくい**: DI コンテナはコンテナ起動時に循環依存を検出してエラーを出す。手動 DI だと自分で気を付ける必要（ただし通常は循環依存が起きる設計自体が悪いので、それに気付くのはむしろ良いこと）

### 代替案として検討したもの

- **Koin**: Kotlin native な DSL で依存宣言する軽量 DI ライブラリ。書く量が少なく綺麗。ただし「DSL の作法を覚える」学習コストがあり、Ktor 公式のチュートリアルも「まずは手動 DI」を推奨している。学習プロジェクトでは手動 DI で「DI の本質」を体感してから、規模が大きくなった時に Koin を検討する順が自然
- **Spring Framework の DI コンテナだけ使う**: Ktor + Spring DI という組み合わせは技術的には可能だが、Ktor 移行の動機（Spring 依存からの脱却）と矛盾する。却下
- **Kodein**: Koin と似た軽量 DI。Kotlin コミュニティで Koin ほどメジャーではない。同種の理由で今回は不採用

## 関連

- [ADR 0008 - Spring Boot から Ktor へ移行する](0008-migrate-from-spring-to-ktor.md) — Ktor 選択の背景
- `backend/src/main/kotlin/com/example/kotlin_todo/Application.kt` — 現時点では依存グラフは空（`/health` のみ）、Phase 4.8 以降で肉付けされる
- Phase 4.11 (テスト戦略再構築) で「手動 DI で mock 差し替えをどう書くか」を扱う予定
- Phase 6 (認証・複数ユーザ) 完了後、規模が大きくなった段階で Koin 再検討の可能性あり
