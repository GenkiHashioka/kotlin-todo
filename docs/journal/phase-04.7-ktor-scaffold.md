# Phase 4.7 (P2) — Ktor 骨組み + Spring 撤去

**ステータス**: P2 完了。Phase 4.7 全体もこれで完了、Phase 4.8 (Exposed でデータアクセス層) に進む
**開始日**: 2026-08-09
**完了日**: 2026-08-10

## 学習目標

- Ktor の起動シーケンス（`embeddedServer` + `Application.module()` パターン）
- Netty エンジンと async / suspend の関係
- Ktor プラグイン方式（`install(...)` で機能追加）
- kotlinx.serialization と `@Serializable` による JSON 変換
- Ktor の `routing { get("/health") { ... } }` DSL の書き方
- `application` Gradle plugin と `mainClass` 指定
- logback + SLF4J の最小構成
- Spring 撤去の実際（削除範囲、残すもの、影響範囲）

## 成果物

### 削除

Spring 実装コード一式（24 ファイル）：
- `backend/src/main/kotlin/com/example/kotlin_todo/KotlinTodoApplication.kt`
- `backend/src/main/kotlin/com/example/kotlin_todo/{controller,service,repository,dto,domain,config,exception}/`
- `backend/src/main/resources/application.properties`
- `backend/src/test/kotlin/com/example/kotlin_todo/{KotlinTodoApplicationTests,AbstractPostgresTest}.kt`
- `backend/src/test/kotlin/com/example/kotlin_todo/repository/`

### 追加

- `backend/src/main/kotlin/com/example/kotlin_todo/Application.kt`（新規、Ktor エントリポイント）
- `backend/src/main/resources/logback.xml`（新規、ログ設定）

### 変更

- `backend/build.gradle.kts` — Spring 依存全撤去、Ktor 依存追加、`kotlin("plugin.serialization")` + `application` plugin 追加
- `docs/decisions/0014-manual-di-over-koin.md`（新規、手動 DI 採用の判断）
- `docs/decisions/README.md` — 0014 を index に追加

### 保持

- `backend/src/main/resources/db/migration/V1__init.sql` — Phase 4.8 で Exposed + Flyway 再設定時にそのまま再利用

## チェックポイント結果

- `./gradlew build` グリーン（Kotlin source が Application.kt のみ、テスト source は 0 ファイル）
- `./gradlew run` で Ktor 起動、`Responding at http://0.0.0.0:8080` を確認
- `curl http://localhost:8080/health` → `{"status":"UP"}` を確認
- Spring 系依存が classpath から完全に消えたことを確認（`./gradlew dependencies` で `spring-boot-*` 系がゼロ）

## 学んだこと

### Ktor と Netty の関係

- Ktor は「HTTP アプリの書き方」を提供する上位レイヤー、Netty は「HTTP プロトコル自体を喋る」低レベル基盤
- Ktor は複数エンジンから選べる設計（Netty / Jetty / CIO / Tomcat）。今回は最も一般的な Netty を採用
- Netty の特徴: 非同期・ノンブロッキング（少ないスレッドで多接続を捌ける）、枯れてる、他のフレームワーク（Vert.x, gRPC, Spring WebFlux）でも採用
- **async の恩恵が出る条件**: 「1 リクエストの CPU 仕事が少ない + I/O 待ち時間が長い + 並列度が高い」の 3 拍子。単なる「軽い」だけでは不十分で、待ち時間があってこそ他リクエストで使い回せる
- **今回の Kotlin Todo では性能面の恩恵は限定的**（並列度低い、DB 待ちも軽い）。ただし **Kotlin の書き心地・coroutines の親和性** は Ktor の方が良い

### Ktor 起動パターン

```kotlin
fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) { json() }
    install(CallLogging)
    install(StatusPages) { }
    routing {
        get("/health") { call.respond(HealthResponse("UP")) }
    }
}
```

- **`fun Application.module()` は拡張関数**: Kotlin 独特の syntax。「既存の型に外部からメソッドを追加する」文法。Ktor では「モジュール = サーバー設定の 1 単位」の慣習
- **`install(...)`**: Ktor のプラグイン適用構文。Spring Boot の `@Configuration` + `@EnableXxx` に相当。「この機能を有効化する」の宣言
- **`call`**: ハンドラ内で暗黙に使える現在のリクエスト・レスポンスを表すオブジェクト
- **`call.respond(...)`**: レスポンス送信。Content Negotiation プラグイン経由で自動 JSON 化される
- **ハンドラのラムダは暗黙に `suspend`**: 今回は同期処理しかないので恩恵はないが、Phase 4.9 で `newSuspendedTransaction { ... }` が入ると suspend chain として機能する

### kotlinx.serialization

- `kotlin("plugin.serialization") version "..."` を build.gradle.kts に追加、`@Serializable data class Foo(...)` でクラスを JSON 対応にする
- Spring 時代の Jackson とは異なるアプローチ: **アノテーションは 1 個だけ、コンパイル時に serializer を自動生成**。ランタイムリフレクションに頼らない → 速い、graalvm native-image でも動く
- 対象クラスは基本的に `data class` にする。特別なフィールド指定なども `@Serializable` 内の annotation で表現できる

### `application` Gradle plugin

- `plugins { application }` で標準の Gradle application plugin を追加
- `application { mainClass.set("com.example.kotlin_todo.ApplicationKt") }` で `./gradlew run` が使えるようになる
- `ApplicationKt` は Kotlin の慣習: **`Application.kt` に `fun main()` を書くと、コンパイル時に `ApplicationKt` というクラスが生成される**（top-level 関数を class にラップする言語仕様）

### Spring 撤去の実際

- 削除ファイルは 24 個。全て Spring/JPA 依存だったので、一気に削除しても中間状態のバグ心配なし（build.gradle.kts で Spring 依存を撤去する PR 内で完結）
- 削除後の backend ディレクトリは非常に小さい: `Application.kt` (~40 行) と `logback.xml`、`V1__init.sql` の 3 つ + Gradle 系だけ。Phase 4.8/4.9 でここに再度肉付けしていく
- 「機能が減った」ように見えるが、これは **Kotlin native な基盤に載せ替えるための整地**。CRUD API 復活は Phase 4.9 で

### JDK 25 + Netty の warning

起動時に以下の警告が出た：
```
WARNING: A restricted method in java.lang.System has been called
WARNING: java.lang.System::loadLibrary has been called by io.netty.util.internal.NativeLibraryUtil
```

JDK 25 で `System.loadLibrary` が「制限メソッド」として警告対象になった。Netty がネイティブライブラリ（epoll 等）をロードする際に使う API。**動作には影響なし**、将来の JDK バージョンでブロック対象になる予定なので、Netty 側の対応を待つ状況。今回は放置。

### IntelliJ でのパッケージ・ファイル作成

Spring 削除の副産物として、`src/main/kotlin/` ディレクトリごと消えていた。IntelliJ での再作成手順を体験：

- **方法 A（採用）**: `src/main` 右クリック → New → Directory → `kotlin` → 作られたフォルダに Sources Root マーク → 右クリック → New → Package → `com.example.kotlin_todo` → その中に New → Kotlin Class/File → File → `Application`
- **方法 B（不採用、本環境では動作せず）**: 事前に「`src/main/kotlin` 右クリック → New → Kotlin Class/File → 名前欄に `com.example.kotlin_todo.Application`（ドット区切りでパッケージ + ファイル名）→ File」で一発作成できる想定だったが、実際にはドットがファイル名の一部として解釈され、`com.example.kotlin_todo.Application.kt` という単一ファイルが作られてパッケージ階層は生成されなかった。IntelliJ のバージョン差 or プロジェクト設定依存の挙動と推測、明確な原因は未特定。**現状のこのプロジェクトでは方法 A の 2 段階手順で作るのが確実**

### Kotlin バージョンを気にしなくていい理由（P1 で登場した論点の再掲）

- Kotlin バージョン = コンパイラ版数。互換性が高く、既存コードはほぼ動き続ける
- Java バージョン = JVM 実行環境の版数。GC・言語機能・使えるライブラリが変わる
- Spring Boot / Ktor は BOM で推奨バージョンを提供 → 触る理由がない限り触らない

## 実運用に関わる既知の課題（今回は対応を見送り）

- **StatusPages プラグインが空**: 例外→HTTP マッピングを何もしていない。Phase 4.9 で本格実装予定
- **依存注入は未実装**: 現状 `/health` しかなく、依存グラフが空。Phase 4.9 で Service/Repository が入る時に手動 DI で組み立てる（ADR 0014 の方針）
- **設定がハードコード**: port 8080 と host `0.0.0.0` が Application.kt に直書き。production 化する時に `application.conf` (HOCON) や環境変数から読む仕組みが要る
- **テストがゼロ**: Phase 4.11 (テスト戦略再構築) で Ktor 用テストを書く。それまでの Phase 4.8-4.10 の間は curl 手動確認頼み
- **DB 未接続**: PostgreSQL は起動可能な状態だが、Application.kt からは接続していない。Phase 4.8 で Exposed 経由の接続を確立する
- **JDK 25 の native access warning**: Netty がネイティブライブラリロードで警告。Netty 側の対応待ち、放置
