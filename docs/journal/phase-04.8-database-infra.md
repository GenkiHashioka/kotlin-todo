# Phase 4.8 (a) — DB インフラ（HikariCP + Flyway + Exposed 接続）

**ステータス**: (a) 完了。(b) で Table 定義 + Repository + Testcontainers に続く
**開始日**: 2026-08-10
**完了日**: 2026-08-11

## 学習目標

- HikariCP（Java 界の標準接続プールライブラリ）の役割と HikariConfig の書き方
- Ktor 環境で Flyway を「素の API」で使う方法（Spring Boot の autoconfig が無い状態でどう呼ぶか）
- Exposed の `Database.connect()` による DataSource 登録
- 3 ステップの順序（HikariCP → Flyway migrate → Exposed 接続登録）の理由
- Kotlin の `object` (singleton) と `.apply { }` scope function の実践

## 成果物

- `backend/build.gradle.kts` — Exposed 3 依存 (`exposed-core`, `exposed-jdbc`, `exposed-java-time`) + HikariCP + Flyway 2 依存 + PostgreSQL JDBC driver を追加
- `backend/src/main/kotlin/com/example/kotlin_todo/db/DatabaseFactory.kt`（新規） — DB 初期化ロジック一式
- `backend/src/main/kotlin/com/example/kotlin_todo/Application.kt` — `module()` 先頭で `DatabaseFactory.init()` を呼ぶ 1 行追加、import 追加

## チェックポイント結果

- `./gradlew build` グリーン
- `docker compose up -d postgres` で PostgreSQL 17 が healthy
- `./gradlew run` 起動時ログで以下を確認：
  - `HikariPool-1 - Starting...` → `Added connection` → `Start completed.`
  - `Flyway: Database: jdbc:postgresql://... (PostgreSQL 17.10)`
  - `Successfully validated 1 migration`
  - `Schema "public" is up to date. No migration necessary.`（V1 は Phase 4.6 で適用済みだった）
  - `Application started in ... seconds` → `Responding at http://0.0.0.0:8080`
- `curl http://localhost:8080/health` → `{"status":"UP"}`（Phase 4.7 の挙動を維持）
- `psql \dt` で `flyway_schema_history`, `users`, `categories`, `todos` の 4 テーブル存在確認

## 学んだこと

### HikariCP と接続プールの意義

- **接続プール = DB 接続を複数個 pre-create して使い回す仕組み**。DB 接続の確立は毎回 TCP handshake + 認証で数十ミリ秒かかる。都度作ると重い、pool しておくと最初の 1 回だけ払う
- HikariCP は JVM 界で **最も速く・堅牢** とされる pool 実装。Spring Boot 時代も内部で使われていた（意識しなかっただけ）
- 今回は `maximumPoolSize = 10` に設定。開発時はこれで十分、production では負荷に合わせて調整
- `HikariConfig` は「接続情報 + プール設定」を保持するオブジェクト。`HikariDataSource(config)` で実際のプールが作られる

### Ktor + Flyway の「素の呼び出し」パターン

Spring Boot 時代（Phase 4.6）は `spring-boot-flyway` module + `spring.flyway.enabled=true` で autoconfig が全部やってくれた。Ktor には autoconfig の仕組みが無いので **自分で明示的に呼ぶ**：

```kotlin
Flyway.configure()
    .dataSource(dataSource)
    .load()
    .migrate()
```

これが「Spring Boot が裏でやってくれてた処理」の実体。**Fluent Builder パターン**（`.configure()` → `.load()` → `.migrate()` と繋げる）で読みやすい。Spring の magic に頼らず自分で書くことで、「Flyway が何をしているか」が明示的に見える。

### 3 ステップの順序が重要

```kotlin
fun init() {
    val dataSource = createHikariDataSource()  // ① 接続プール
    Flyway.configure().dataSource(dataSource).load().migrate()  // ② schema 更新
    Database.connect(dataSource)  // ③ Exposed に登録
}
```

- **① 接続プール** が無いと、Flyway も Exposed も DB に喋れない
- **② Flyway migrate** の時点でスキーマが最新化される。Exposed の Table 定義は「既存 schema を反映するもの」なので、schema がなければ意味が無い
- **③ Exposed に登録** することで、以降のコードで `newSuspendedTransaction { ... }` が「デフォルトの Database」として今作った DataSource を使うようになる

もし ② を飛ばすと、Exposed が「テーブル無い」で落ちる。もし ③ を飛ばすと Exposed が「Default database is not initialized」エラーで落ちる。**この 3 ステップは Kotlin + Ktor + Exposed の定番セットとして覚える**。

### `object` = Kotlin の singleton

```kotlin
object DatabaseFactory {
    fun init() { ... }
}
```

Java だと singleton パターンを書くのに private constructor + `static getInstance()` + volatile field みたいな儀式が必要。Kotlin は `object` の一言で言語レベルで singleton になる。**呼び出し側は `DatabaseFactory.init()` のように static っぽく使える**。

状態を持たないユーティリティ的なクラス（今回の DB 初期化）に向く用途。将来 Repository は `class` で書く（それぞれインスタンスを持つため）。

### `.apply { }` = scope function

```kotlin
val config = HikariConfig().apply {
    jdbcUrl = "..."
    username = "..."
    password = "..."
    driverClassName = "..."
    maximumPoolSize = 10
}
```

Java 風に書くと `config.setJdbcUrl(...)` を 5 回繰り返す形。Kotlin の `.apply { }` は「対象オブジェクトのプロパティを設定して、その対象を返す」の scope function。**Builder パターンっぽい書き心地を、Builder クラスを書かずに実現**できる。

Kotlin には他にも `.let`, `.also`, `.run`, `.with` の scope function があり、それぞれ用途で使い分ける。今回のように「オブジェクトを作って設定する」ケースは `.apply`。

### Flyway の冪等性（idempotent）を実感

初回起動ログ（Phase 4.6b で作った時）は `Successfully applied 1 migration to schema "public"` だったが、今回は `Schema "public" is up to date. No migration necessary.` だった。

これは Flyway が **`flyway_schema_history` テーブルを見て「V1 は既に適用済み」と判定** した結果。同じ migration を 2 度実行しない仕組みが、実際の起動ログで確認できた。

**将来 V2 を追加した時**は「Schema "public" is up to date」ではなく「Applied 1 new migration」に変わる。差分だけを適用する運用が Flyway の本質。

### Ktor に「initialization hook」は無い

Spring Boot だと `@PostConstruct` や `CommandLineRunner` みたいな「起動時に一度だけ実行する」フックがあった。Ktor には同等の magic なフックが無いので、**`Application.module()` の中に自分で書く**のがイディオム。

module() は「Ktor 起動時に一度だけ呼ばれる」ので、そこで DatabaseFactory.init() を呼べば「起動時 DB 初期化」が実現される。**「フレームワークが暗黙にやってくれてたこと」を自分の手で組み立てる**のが Ktor + 手動 DI の思想と一致（ADR 0014 の方針）。

### 依存が全部バージョン明示になった理由

Spring Boot 時代は BOM が管理してくれた依存が多かった。Ktor に移行した今、`ktor-bom` は Ktor サブモジュール群だけを管理し、Exposed / HikariCP / Flyway / PostgreSQL は自前でバージョン指定する必要がある。

今回追加した version：
- Exposed 0.61.0（2025 年後半 released）
- HikariCP 6.2.1
- Flyway 11.1.0
- PostgreSQL JDBC 42.7.4

**「バージョンが古くなった時に自分で判断して上げる」責任が自分に移った**。Spring Boot の便利さの裏側で捨てた部分の 1 つ、と言える。

## 実運用に関わる既知の課題（今回は対応を見送り）

- **接続情報がハードコード**: `jdbcUrl` / `username` / `password` が DatabaseFactory に直書き。テスト（PR (b) の Testcontainers）と本番で切り替える仕組みが要る。Ktor の `application.conf` (HOCON) 経由で外部化するのが正道、が今は動作優先で保留
- **Repository 未実装**: DB 接続はできたが、まだ SQL を発行するコードが無い。PR (b) で Table 定義 + Repository で肉付けする
- **テスト無し**: PR (b) で Testcontainers を再導入して Repository テストを書く
- **プール設定が固定**: `maximumPoolSize = 10` は開発向けの適当値。production 化する時は負荷特性に応じて調整、モニタリング（Micrometer 等）と連動させる
- **起動時 migration の失敗ハンドリング未考慮**: PostgreSQL が起動していない状態で Ktor を起動すると、DatabaseFactory.init() で例外が飛んで Ktor 全体の起動失敗になる。「migration 失敗時にどう再起動戦略を取るか」は production 課題
- **Netty の JDK 25 warning 継続**: 前 Phase から変わらず、Netty 側の対応待ち
