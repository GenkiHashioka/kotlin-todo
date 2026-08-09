# 0011 - スキーマ管理を Flyway に集約する（`ddl-auto=none`）

**ステータス**: 採用
**日付**: 2026-08-09

## Context（背景・何を解決したいか）

Phase 4.5 完了時点まで、backend の DB スキーマは Hibernate の `ddl-auto`（H2 in-memory の embedded 検出により実質 `create-drop` が働く）に任せていた。Entity クラスから毎回起動時に schema を生成し、停止時に破棄する運用。学習用途の H2 では十分だが、Phase 4.6 で PostgreSQL に切り替えるにあたり、schema をどう管理するかを決める必要があった（判断の全体順序は [ADR 0010](0010-db-migration-before-framework-swap.md) 参照）。

`ddl-auto` を PostgreSQL でもそのまま使う選択肢もあるが、以下の理由で不十分と判断した：

- `ddl-auto=create` / `create-drop` は破壊的で、開発中の DB データを毎回消してしまう
- `ddl-auto=update` は「Entity から DDL を推測して差分適用」だが、Hibernate の推測はカラム削除や rename に弱く、production では危険とされている
- `ddl-auto=validate` は「Entity と DB スキーマの一致確認だけ」で、schema 変更手段としては別の仕組みが要る
- schema の履歴（いつ、誰が、どの migration を適用したか）が残らないので、複数環境（開発 / staging / production）での状態把握が困難
- 将来 Ktor + Exposed に移行しても schema 管理は独立させたい。Entity 側のライブラリ（JPA / Exposed）を変えても migration ファイルは資産として残る

## Decision（何を決めたか）

DB スキーマ管理を **Flyway に一元化** する。具体的には：

- `build.gradle` に `org.flywaydb:flyway-core` + `org.flywaydb:flyway-database-postgresql` を追加
- `build.gradle` に **さらに `org.springframework.boot:spring-boot-flyway`** を追加（Spring Boot 4.x の modularization により、旧来 `spring-boot-autoconfigure` に同梱されていた `FlywayAutoConfiguration` が独立モジュールに分離された。3.x では Flyway を classpath に置けば自動起動していたが、4.x では **Flyway 統合モジュールを明示的に依存させる**必要がある。H2 の `spring-boot-h2console`、Data JPA の `spring-boot-data-jpa` と同じパターン）
- `application.properties` に `spring.jpa.hibernate.ddl-auto=none` を明示 — Hibernate は DDL を発行しない
- `application.properties` に `spring.flyway.enabled=true` と `spring.flyway.locations=classpath:db/migration` を明示（Spring Boot autoconfigure でも動くが意図を明示）
- `backend/src/main/resources/db/migration/V1__init.sql` に初期 schema（users / categories / todos + FK + 複合 UNIQUE + インデックス）を記述
- migration file 命名規則は Flyway 標準 (`V{version}__{description}.sql`) に従う
- Spring Boot 起動時に Flyway が自動実行されるフロー（`bootRun` するだけで migration が走る）

**Flyway が管理する schema**: 全ての DDL（CREATE TABLE / ALTER TABLE / CREATE INDEX / DROP TABLE 等）。**JPA Entity 側は「既存 schema にマップする」役割**に限定される。Entity 側の変更（新規カラム追加、rename 等）は必ず対応する新規 migration file (`V2__...`, `V3__...`) を書いてから Entity を修正する運用。

## Consequences（この決定によって何が得られ、何を犠牲にしたか）

### 得られるもの

- **schema 変更の履歴が DB に残る**: Flyway が `flyway_schema_history` テーブルを自動作成し、いつどの migration が適用されたかを記録。「今この DB は何バージョンか」が SQL 一発で分かる
- **schema 変更が明示的なファイル**として残る: migration file を PR で追加する形になり、code review で schema 変更を確認できる。`ddl-auto=update` の暗黙推測に比べて、意図がドキュメント化される
- **開発中のデータが消えない**: 起動時に `create-drop` されないので、`bootRun` の再起動でも保存済み Todo が残る
- **Ktor + Exposed 移行後も資産が生きる**: Flyway migration は SQL ベースで ORM 非依存。Phase 4.7 以降で Exposed に切り替えても、`V1__init.sql` はそのまま使え、Exposed の Table 定義がこの schema にマップする形になる（[ADR 0010](0010-db-migration-before-framework-swap.md) の Consequences 節と整合）
- **複数環境の schema 一貫性**: 将来 staging / production を作った時に、同じ migration file 群を流せば同じ schema になる保証

### 犠牲にするもの

- **migration file の運用コスト**: Entity を変更するたびに対応する `V{n+1}__*.sql` を手書きする必要がある。`ddl-auto=update` の「Entity 書き換えたら勝手に追随」の手軽さは失う（が、これは production 運用としては安全側の選択）
- **依存が 3 つ増える**: `flyway-core`, `flyway-database-postgresql`, `spring-boot-flyway`。Flyway 10+ で db-specific モジュールが分離、Spring Boot 4.x で integration モジュールが分離、という 2 段階の modularization を経た結果。全てバージョン指定不要（Spring Boot BOM が管理）で保守負担は軽微だが、依存追加漏れがあると autoconfig が起動せず「Flyway が居るのに migrate が走らない → schema 空 → JPA が `relation "users" does not exist` で落ちる」という Phase 4.6 実装時に踏んだ落とし穴が起きる
- **開発時にも DB 起動が必須**: 以前は H2 in-memory で `bootRun` 単体で完結していたが、これからは `docker compose up -d postgres` を先に叩く必要がある（[ADR 0010](0010-db-migration-before-framework-swap.md) で覚悟済み）

### 代替案として検討したもの

- **`ddl-auto=validate` + 手書き `schema.sql`**: Spring Boot 起動時に classpath の `schema.sql` を実行、Hibernate は差分検証のみ。migration 履歴が残らない、複数バージョンの追跡ができないため却下
- **Liquibase**: Flyway の対抗馬。XML/YAML/JSON DSL で migration を記述、Flyway より抽象度は高いが、SQL を直接書ける Flyway の方が「学習者が SQL を意識する」効果が高い。Kotlin 系プロジェクトでは Flyway の方が採用例が多い印象もあり、Flyway 採用
- **`ddl-auto=update` を production まで押し通す**: 短期は楽だが Hibernate の推測は rename / drop に弱く、破壊的変更で事故る事例が多い。学習プロジェクトとはいえ、実務で通用しない習慣を最初に付けたくないため却下
- **Gradle Flyway plugin (`org.flywaydb.flyway`) を使い明示的に `./gradlew flywayMigrate` する**: CI 統合や手動制御には便利だが、開発時は `bootRun` するだけで migration が走る Spring Boot autoconfigure の方が素直。CI 統合は Phase 7 以降の課題として保留

## 関連

- [ADR 0010 - DB 移行（PostgreSQL + Flyway + Testcontainers 化）を Web フレームワーク移行より先に行う](0010-db-migration-before-framework-swap.md)
- [ADR 0001 - enumはORDINALではなくSTRINGでDB保存する](0001-enum-storage-as-string.md) — `V1__init.sql` の `priority` / `status` を `VARCHAR(20)` にする根拠
- [ADR 0002 - Userを削除したら関連するTodo/CategoryもCASCADE削除する](0002-cascade-delete-on-user.md) — `V1__init.sql` の `ON DELETE CASCADE` の根拠
- [ADR 0003 - Category名はユーザーごとに一意にする](0003-category-name-unique-per-owner.md) — `V1__init.sql` の複合 UNIQUE 制約の根拠
- [ADR 0004 - Categoryを削除したら、それに紐づくTodoのcategoryはnullにする](0004-category-delete-sets-todo-category-null.md) — `V1__init.sql` の `ON DELETE SET NULL` の根拠
- `docs/db-schema.md` — テーブル定義とインデックス方針の一次ソース、`V1__init.sql` の DDL はこれを反映
- 次: ADR 0012（Testcontainers 採用）を PR (c) で追加予定
