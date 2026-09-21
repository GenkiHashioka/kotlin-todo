# Phase 4.6 — Testcontainers 化 + H2 撤去

**ステータス**: PR (c) 完了。Phase 4.6（DB 移行）全体もこれで完了、Phase 4.7 (Ktor 骨組み) に進む
**開始日**: 2026-08-09
**完了日**: 2026-08-09

## 学習目標

- Testcontainers の仕組みと、実 DB でテストする意義
- Spring Boot の `@ServiceConnection` による Testcontainer 自動配線
- `companion object` を使った JVM 内テストコンテナ共有パターン
- `@DataJpaTest` のデフォルト挙動（embedded DB への置き換え）を無効化する方法
- Spring Boot 4.x の modularization に対応した依存宣言（testcontainers 系）
- Kotlin の `@Transactional` クラスに `open` が必要な理由

## 成果物

- `backend/build.gradle` — Testcontainers 依存 3 つ + BOM 追加、`com.h2database:h2` 削除
- `backend/src/test/kotlin/com/example/kotlin_todo/AbstractPostgresTest.kt`（新規）— 全テスト共通の PostgreSQL コンテナ基底クラス
- `backend/src/test/kotlin/com/example/kotlin_todo/KotlinTodoApplicationTests.kt` — `AbstractPostgresTest` 継承に変更
- `backend/src/test/kotlin/com/example/kotlin_todo/repository/TodoRepositoryTest.kt` — `AbstractPostgresTest` 継承、`@AutoConfigureTestDatabase(replace = Replace.NONE)` 追加、`open class` に変更、Thread.sleep を 10ms → 100ms に増やす
- `docs/decisions/0012-testcontainers-for-integration-test.md`（新規）
- `docs/decisions/README.md` — 0012 を index に追加
- 本 journal

## チェックポイント結果

- `./gradlew test` グリーン（4 テスト green）
- Testcontainers が `postgres:17` コンテナを起動し、Flyway が V1__init.sql を適用、テストがそれに対して走ることをログで確認
- H2 依存が classpath から消えたことを確認（`./gradlew dependencies` で `com.h2database` が出ない）

## 学んだこと

### Testcontainers の基本発想

- Testcontainers は「テスト実行時に Docker で本物の DB / MQ / Redis を立ち上げる」ライブラリ。in-memory mock や embedded DB とは違い、**production と同じミドルウェアの本物** を使う
- テストコード側は `PostgreSQLContainer("postgres:17")` を宣言するだけ。JUnit と統合すれば `@Container` フィールドの生存期間に合わせてコンテナが start/stop する
- 本番と同じ DB でテストする最大の効用は「SQL 方言差や DB 固有の挙動が起因の bug を事前検出できる」こと。H2 で通ったテストが production で落ちる、という古典的問題を構造的に消せる

### `@ServiceConnection` の魔法

- Spring Boot 3.1 で導入された機能。`@Container` 付きフィールドに `@ServiceConnection` を追加するだけで、Spring がコンテナの接続情報（URL / user / password）を自動で DataSource プロパティに注入する
- 旧来は `@DynamicPropertySource` で手動に property 上書きコードを書く必要があった。それが annotation 1 個で済むようになった
- Spring Boot 4.x では、この機能を使うために `spring-boot-testcontainers` module を明示的に依存に足す必要がある（3.x 時代は `spring-boot-autoconfigure` に同梱）

### JVM 内で 1 コンテナ共有するパターン

- Kotlin の `companion object` はクラスロード時に一度だけ初期化される。この特性を活用し、`AbstractPostgresTest` の companion object に `@Container` 付き static コンテナを 1 個置くと、そのクラスを継承した全テストで同じコンテナインスタンスが使われる
- 結果、コンテナ起動コスト（`postgres:17` の場合 ~5 秒）を JVM 生存期間で 1 回だけ払えばよい。テストクラスが 10 個あっても 20 個あっても同じ
- 各テストで DB 状態が混ざるのが心配な場合は `@Transactional`（テスト末で rollback）や明示的な `TRUNCATE` を使う。今回の 3 テストは元々相互独立なので不要だった

### `@DataJpaTest` は暗黙に DB を置き換える

- Spring Boot の `@DataJpaTest` は「Repository slice test」で、必要最小限のコンテキストしかロードしない。**そのデフォルト動作の 1 つに「classpath の embedded DB (H2/HSQL/Derby) を検出して DataSource を置き換える」がある**
- H2 依存を classpath に残していると、この置換が働いて H2 で走ってしまう（PR (b) までの状態がまさにこれ）
- 実 DB に対して走らせたければ `@AutoConfigureTestDatabase(replace = Replace.NONE)` を追加する。「置換するな、application.properties の DataSource そのままを使え」の宣言

### Spring Boot 4.x で踏んだ 3 つの落とし穴

このセッションで一番学びが大きかった部分。

1. **`spring-boot-testcontainers` module が必要**
   `flyway-core` を classpath に置いても Flyway autoconfig が起動しなかった PR (b) と全く同じ現象で、Testcontainers 統合も別モジュール。Boot 4.x では「integration が欲しかったらそのモジュールを明示的に足す」思想に統一されている

2. **Testcontainers BOM を `platform()` で明示する必要がある**
   Boot 3.x の BOM は `org.testcontainers:*` のバージョンも管理していたが、4.x BOM は管理範囲を縮小。`testImplementation 'org.testcontainers:junit-jupiter'` だけだと Gradle が「バージョン指定されていない、管理者もいない」で resolve に失敗する。`testImplementation platform('org.testcontainers:testcontainers-bom:1.20.4')` を追加して Testcontainers サブモジュール群のバージョンを揃える
   - `platform(...)` は Gradle 特有の syntax で「classpath には何も追加しない、ただ version 管理だけ提供する」の宣言
   - BOM の値そのもの (1.20.4) は個別 artifact のバージョン集合を提供する
   - Testcontainers サブモジュール同士は internal API で密結合してるので、version 揃わないとランタイム事故になる

3. **Kotlin の `@Transactional` クラスは `open class` として宣言する必要がある**
   Spring の `@Transactional` は proxy（サブクラス）を作って動作するので、対象クラスがサブクラス化可能である必要がある。Kotlin はクラスがデフォルト `final`（サブクラス化不可）なので、通常は `kotlin.plugin.spring` プラグインが `@Transactional` 付きクラスを自動的に `open` にしてくれる（all-open 機能）
   - しかし `AbstractPostgresTest` を継承する構成にすると、この自動 open が期待通りに効かない挙動を確認（原因は未特定、Kotlin plugin と Spring proxy 生成のタイミング干渉と推測）
   - 明示的に `open class TodoRepositoryTest(...)` と書くことで解決
   - Java だと逆で「クラスはデフォルトで継承可能」なので Spring 側で気にする必要がない。Kotlin と Spring の組み合わせで固有に発生する話

### Gradle incremental compile の落とし穴

- Kotlin ファイルに annotation を追加した後、incremental compile が古い bytecode を保持したままだと期待した挙動にならない
- 今回、`open class` を追加した直後の `./gradlew test` は Thread.sleep(10) → 100ms 変更でも通らなかったが、`./gradlew clean test` で通った。sleep 増量が本当に効いたのか、単に clean で bytecode が正しく再生成されたのかは切り分け不能
- 教訓: 「annotation 追加や継承構造を変えた後で挙動がおかしければ、まず clean」

### エラーメッセージから原因を掴む道筋

このセッションで実際に効いた診断フロー：

1. **`./gradlew test` の出力**: 表面的な「BUILD FAILED」まで
2. **テスト結果 XML** (`build/test-results/test/TEST-*.xml`): 各テストの失敗原因（例外メッセージ、stacktrace）が入っている
3. **CONDITIONS EVALUATION REPORT** (`debug=true` プロパティで有効化): Spring autoconfig の判定結果、「なぜ動くべきものが動かないか」の一次情報
4. **`~/.gradle/caches/modules-2/files-2.1/` の目視**: 実際に classpath に何が居るか（ライブラリのモジュール名も含めて）を確認

段階を追って絞り込むと、Spring / Kotlin / Gradle が絡む複雑な失敗も切り分けられる

### 依存の可視化

- `./gradlew dependencies --configuration testRuntimeClasspath | grep <library>` で classpath の依存が確認できる。「依存を書いたのに resolve されない」時の一次情報
- `./gradlew --refresh-dependencies build` で依存 cache を無視して再解決を強制。stale cache 疑いの時に使う

## 実運用に関わる既知の課題（今回は対応を見送り）

- **`Thread.sleep(100)` で timestamp 比較テストが安定化**: 元は 10ms で書かれていた「updatedAt 更新テスト」が Testcontainers PostgreSQL に切り替え後 flaky になった疑いがあり、100ms に増量した。根本的には `Clock` を注入して時刻を制御可能にする、`Awaitility` で条件成立を待つ、といった書き方が堅牢。Phase 4.6 のスコープ外として保留
- **Docker daemon が停止していると testは実行不可**: 開発機の常時起動を前提としているが、明示的にドキュメント化していない。将来 README に「テスト実行には Docker が必要」の一文を足す価値あり
- **CI 未整備**: GitHub Actions で Testcontainers を回す設定はまだ書いていない。将来 CI を導入する時に、Docker runner の権限設定が必要
