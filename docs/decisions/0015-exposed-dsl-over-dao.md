# 0015 - Exposed の DAO API ではなく DSL API を採用する

**ステータス**: 採用
**日付**: 2026-08-11

## Context（背景・何を解決したいか）

Exposed には性格の違う 2 系統の API がある：

- **DAO API**: JPA / Hibernate 経験者に近い感覚。`Entity` クラスを継承した Kotlin クラスで DB 行を表現、`.new { ... }` / `.findById()` などのメソッド、変更検知（プロパティ書き換えが自動 UPDATE に）、`by relation` によるリレーション navigation
- **DSL API**: JOOQ や MyBatis に近い感覚。`Table` オブジェクトの列を直接使い、`Users.insert { ... }` / `Users.selectAll().where { ... }` などで SQL を組み立てる。行は `ResultRow` として扱い、ドメインクラスへの変換は自分で書く

Phase 4.8 でデータアクセス層を新規構築するにあたり、どちらを選ぶかを決める必要があった。

参考: DAO API の書き方（採用しなかった側）:

```kotlin
object Users : LongIdTable("users") {
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val createdAt = datetime("created_at")
}

class User(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<User>(Users)
    var email by Users.email
    var passwordHash by Users.passwordHash
    var createdAt by Users.createdAt
}

transaction {
    val user = User.new {
        email = "u@u.com"
        passwordHash = "hash"
        createdAt = LocalDateTime.now()
    }
    user.email = "changed@u.com"  // 変更検知で自動 UPDATE
}
```

DSL API の書き方（採用）:

```kotlin
object Users : Table("users") {
    val id = long("id").autoIncrement()
    val email = varchar("email", 255).uniqueIndex()
    // ...
}

// Entity クラスはない、ドメイン data class を別途定義
data class User(val id: Long, val email: String, ...)

class UserRepository {
    suspend fun create(email: String, ...): User = newSuspendedTransaction {
        val id = Users.insert { it[Users.email] = email; ... } get Users.id
        User(id = id, email = email, ...)
    }
}
```

## Decision（何を決めたか）

**DSL API を採用**し、DAO API は使わない。具体的には：

- Table 定義は素の `Table("...")` を継承（`LongIdTable` などの DAO 前提の基底クラスは使わない）
- `Users.new { }` などの Entity メソッドは書かない
- Repository は手書きクラスで DSL を呼ぶ（`Users.insert { }`, `Users.selectAll().where { }` など）
- ドメインクラスは `domain/` パッケージに独立した `data class` として定義（Table とは完全に分離）
- Table → ドメイン の変換は `private fun ResultRow.toUser(): User` のような拡張関数で Repository 内に閉じる

## Consequences（この決定によって何が得られ、何を犠牲にしたか）

### 得られるもの

- **「明示的な SQL 思考」を身につけられる**: Phase 4 の JPA/Hibernate 時代は「Entity をいじれば裏で SQL が発行される」暗黙モデルだった。DSL では `Users.insert { }` を書いた時点で INSERT が発行されることが明示的で、「今どの SQL が走るか」を自分の頭で追える
- **JPA との思想の対比を体験できる**: ADR 0008 で選んだ「Kotlin 言語そのものを深めたい」学習方針との相性。「Entity と DB 行を同一視する JPA」と「Table 定義とドメインクラスを別レイヤーに分ける Exposed DSL」の対比は、永続化パターン自体の理解を深める
- **ドメインクラスが純粋なデータ保持者になる**: DAO 版だと `User` は Exposed の `LongEntity` を継承した「DB 依存クラス」になる。DSL 版だと `User` は素の `data class`、Exposed とは無関係。ドメインをフレームワークから切り離せる（**Clean Architecture 的な依存の向き**）
- **coroutines との親和性**: DAO API は Exposed の EntityCache という trans-transaction 状態を持つ。coroutine が suspend / resume する中で EntityCache の一貫性がどう扱われるかは複雑になりやすい。DSL API は「ResultRow を取ったらそこで data class に変換して終わり」で、状態を持たない。suspend 貫通の設計が素直
- **immutable data class が使える**: DAO の Entity は `var` プロパティ + 変更検知が前提。`val` にはできない。DSL 版はドメイン `data class` を `val` で組めるので、Kotlin native な imutability の思想と一致

### 犠牲にするもの

- **mapping コードを手書きする必要がある**: `ResultRow → domain data class` の変換関数を Repository ごとに書く。DAO なら `User.findById(1)` で完結するところ、DSL では 3〜10 行の変換が要る。Phase 4 の Bean Validation / DTO と Entity の変換に近い作業量が発生
- **リレーション navigation が無い**: DAO なら `user.categories` で User のカテゴリ一覧を lazy load できる。DSL では `categoryRepo.findByOwner(user.id)` のように明示的に呼ぶ。書く量は増えるが、N+1 問題や lazy load 罠が構造的に消える
- **派生クエリ機能が無い**: Spring Data JPA の `findByEmail(email: String)` を interface に書くだけで自動実装される機能は Exposed には無い。DSL では `UserRepository.findByEmail` を手書きする必要あり。Phase 4 との対比では書く量が増えているように見える
- **Exposed DAO 固有の便利機能を諦める**: EntityCache による同一性、変更検知、リレーションの lazy load、`refresh()` などの便利 API。学習段階では「便利さを捨てて透明性を取る」の判断だが、大規模プロジェクトでは判断が変わる可能性あり

### 代替案として検討したもの

- **Exposed DAO API**: JPA からの移行障壁は最も低い。ただし本プロジェクトは「Kotlin 言語自体を学ぶ」目的（ADR 0008）で、JPA っぽさを継続することは学習効果が薄い。また DAO の EntityCache と coroutines suspend の相互作用は情報が少なく、詰まった時に自力解決が難しい懸念あり。却下
- **JOOQ**: Kotlin から使える別の SQL DSL ライブラリ。Java 中心のコミュニティで、Kotlin idiomatic ではない。Exposed のほうが JetBrains 公式で Kotlin ネイティブ、選択肢としては明確に Exposed 優位。JOOQ は選ばず
- **手書き JDBC**: 「最も低レベルで明示的」だが、type safety / null 安全の恩恵を失う。Exposed の DSL は「JDBC の透明性 + Kotlin の型安全」の中間層として最適。手書き JDBC は選ばず
- **Ktor プロジェクトで Hibernate/JPA を継続**: 技術的には可能だが、ADR 0008 の Ktor 移行動機（Kotlin native なスタック）と矛盾する。却下

## 関連

- [ADR 0008 - Spring Boot から Ktor へ移行する](0008-migrate-from-spring-to-ktor.md) — Kotlin native なスタック採用の背景
- [ADR 0011 - スキーマ管理を Flyway に集約する](0011-flyway-for-schema-management.md) — Table 定義は「schema の宣言」であって「DDL 生成源」ではない、の前提
- `backend/src/main/kotlin/com/example/kotlin_todo/db/{Users,Categories,Todos}.kt` — Table 定義の実物
- `backend/src/main/kotlin/com/example/kotlin_todo/domain/*.kt` — ドメインクラスの実物（Exposed 依存無し）
- `backend/src/main/kotlin/com/example/kotlin_todo/repository/*Repository.kt` — DSL API を使った Repository 実装
- Phase 4.9 で Service 層と Ktor Routing を組み立てる時、DSL パターンが `Application.module()` の suspend chain と綺麗に繋がる予定
