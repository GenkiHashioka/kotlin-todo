# Phase 3 — JPA/Hibernateでのドメインモデリング

**ステータス**: 完了
**開始日**: 2026-08-01
**完了日**: 2026-08-01

## 学習目標

- `@Entity`, `@Id`/`@GeneratedValue`
- `@ManyToOne`（lazy/eager、`optional`属性）
- Spring Data JPA repository
- なぜEntityはdata classにしないか（Phase 1から持ち越し）
- `@Transactional`基礎

## 成果物

- `docs/db-schema.md` — ER図・テーブル定義・インデックス方針
- `docs/decisions/0001〜0003` — enum保存方式、CASCADE削除、Category名の一意制約のADR
- `domain/entity/`（`User`, `Category`, `Todo`）+ `domain/enums/`（`Priority`, `TodoStatus`）
- `config/JpaAuditingConfig.kt`（`@EnableJpaAuditing`）
- `repository/`（`UserRepository`, `CategoryRepository`, `TodoRepository`）
- `test/repository/TodoRepositoryTest.kt` — `@DataJpaTest`で3ケース（関連の保存・取得／`updatedAt`自動更新／Category削除時のTodo.category自動null化）、いずれもグリーン

## チェックポイント結果

`./gradlew test`実行後、`build/test-results/test/TEST-...TodoRepositoryTest.xml`で`tests="3" failures="0" errors="0"`を確認。`build/reports/tests/test/index.html`のHTMLレポートでも成功を確認できる状態。

## 学んだこと

### ディレクトリ構成・アーキテクチャ
- レイヤードパッケージ構成（`domain/` `repository/` `service/` `controller/` `config/`）と機能別パッケージ構成の違い、このプロジェクト規模ではレイヤード構成が妥当
- `domain`パッケージの中でも`entity/`（集約ルート）と`enums/`（共有される値の語彙）を分けた方が、将来（Phase 6の`Role`等）を見据えて整理しやすい
- `@SpringBootApplication`は`@Configuration`を含むため技術的にはメインクラスに`@Enable*`系を積めるが、責務分離のため専用の`@Configuration`クラス（`config/`）に切り出す方が良い。`@ComponentScan`はメインクラスのパッケージ以下を再帰的に見て回るため、サブパッケージに置いても自動的に検出される

### なぜEntityをdata classにしないか
- `data class`が自動生成する`equals()`/`hashCode()`は全プロパティ（可変な`var`含む）を使うため、保存前（`id = null`）に`Set`/`Map`へ入れたEntityを保存すると`id`が変わり`hashCode()`も変わってしまい、コレクション内で見つからなくなる（迷子になる）
- `toString()`も全プロパティを対象にするため、`FetchType.LAZY`な関連（例: `Todo.owner`）に対して呼ぶと意図せず遅延ロードが走ったり、セッションが閉じた後に呼ぶと`LazyInitializationException`が発生し得る
- `copy()`は同じ`id`を持つ別インスタンスを生成してしまい、「1つの永続化コンテキストでは同じDB行は同じインスタンス」というHibernateの前提を崩す
- data classは「構造的等価性（中身が同じなら同じ）」、Entityは「同一性（IDが同じなら同じ）」で扱うべきオブジェクトであり、設計思想が根本的に噛み合わない
- 通常の`class`はデフォルトで参照の同一性を使うため、同一セッション内では安全に動作する。これが今回のEntityが通常の`class`である理由
- ただし`@Entity`を`data class`に付けること自体はコンパイルエラーにもJPAのバリデーションエラーにもならず、単純なCRUDは何も問題なく動いてしまう。危険は「Set/Mapに入れる」「toString()を呼ぶ」「copy()を使う」「双方向関連で無限再帰が起きる」といった特定の使い方をした時だけ顕在化するため、動作確認だけでは気づけない「時限爆弾型」のバグになりやすい

### `@Transactional`の基礎
- `@Transactional`はメソッドの中身を書き換えるのではなく、Springがそのクラスの「プロキシ（代理オブジェクト）」を生成し、メソッド呼び出しの前後にトランザクション開始・コミット/ロールバックの処理を差し込むAOP（Aspect-Oriented Programming）の仕組み
- デフォルトでは非チェック例外（`RuntimeException`・`Error`）でのみロールバックし、チェック例外ではロールバックしない（`rollbackFor`で変更可能）
- プロキシ経由でないと機能しないため、**同じクラス内で自分自身のメソッドを直接呼び出す（self-invocation）と`@Transactional`が効かない**。外部（別クラス）からの呼び出しであれば問題なくプロキシを経由する
- デフォルトの伝播設定（`REQUIRED`）は「既存のトランザクションがあればそれに参加し、無ければ新規作成する」という意味。`@Transactional`が付いたメソッドから呼ばれる内部メソッドは、自身に`@Transactional`が無くても同じトランザクションに参加する
- `@DataJpaTest`が各テストメソッドを自動ロールバックしていたのは、内部的にこの`@Transactional`の仕組みを使って各テストを1つのトランザクションとして実行しているため

### JPAマッピングの落とし穴
- DB制約（NOT NULL）・Kotlinの型（non-null/nullable）・JPAの`@ManyToOne(optional)`/`@JoinColumn(nullable)`の3つを一致させる必要がある
- Hibernateはリフレクションでフィールドに直接値をセットするため、Kotlinのコンパイル時null安全チェックをすり抜けられる。非null型のフィールドに実際にはnullが入り得るケースがあり、通常Kotlinでは起きないはずの`NullPointerException`が発生し得る
- `ON DELETE CASCADE`はJPA標準の`CascadeType`（アプリケーションレベル、ORM経由の削除でのみ働く）と、Hibernate独自の`@OnDelete(action = OnDeleteAction.CASCADE)`（DBの外部キー制約自体に`ON DELETE CASCADE`を持たせる）の2種類があり、目的が異なる。DB制約として保証したい場合は後者

### 自動更新タイムスタンプ
- `@CreatedDate`/`@LastModifiedDate`は、`@EntityListeners(AuditingEntityListener::class)`（Entity側）と`@EnableJpaAuditing`（設定クラス側）の両方が揃って初めて機能する。どちらか一方が欠けると、デフォルト値のおかげで一見動いているように見えてしまい、気づきにくい
- `@PrePersist`（`@CreatedDate`用）は`persist()`呼び出し時に同期的に発火するが、`@PreUpdate`（`@LastModifiedDate`用）はflush時に発火するため、更新後すぐに検証したい場合は`saveAndFlush()`で明示的にflushさせる必要がある

### 永続化コンテキスト（1次キャッシュ）と`@OnDelete`の関係
- 永続化コンテキストは、Hibernateが「今のトランザクションで読み込んだ／保存したエンティティ」を`(エンティティクラス, ID)`をキーに覚えておく内部的なMap。エンティティがこのMapに乗っている間（＝管理対象/managed状態）、Hibernateはフィールドの変更を監視し続け、flush時に自動でSQLを生成する（ダーティチェック）。生JDBCの「SELECTしたら後はただのオブジェクト」という感覚とはここが根本的に違う
- `@OnDelete(action = OnDeleteAction.SET_NULL)`は**DBの外部キー制約自体**に`ON DELETE SET NULL`を持たせるだけの設定で、Hibernateを介さない生SQLでの削除でもDBエンジンが自動でON DELETEアクションを実行してくれる。一方でHibernateは、flush前に永続化コンテキスト内のオブジェクトグラフを自分自身でも整合性チェックしており、この2つは互いの存在を関知しない別レイヤーの仕組み
- そのため、削除対象のエンティティ（例: Category）をまだ参照している別のエンティティ（例: Todo）が永続化コンテキストに管理対象として残ったまま削除・flushすると、`TransientPropertyValueException`（`CascadeType.REMOVE`が無い関連に対する防御的エラー）が発生する。`@OnDelete`を付けてもこのチェックは回避されない
- 対処法は、削除対象を参照しているエンティティを削除前に永続化コンテキストから外す（`entityManager.clear()`など）こと。管理対象から外れていれば、Hibernateはオブジェクトグラフの整合性チェック対象にせず、素直にDBへDELETE文を発行する。DB側の`ON DELETE`アクションはHibernateの関知しないところで実行されるため、削除後に取得し直す際も、永続化コンテキストがクリアされていないと更新前の値がキャッシュから返ってきてしまう点に注意が必要

### Repository・Optional・テスト
- `interface`は実装を持たない契約なので、Springが動的プロキシで実装を用意できる。`abstract class`だと単一継承の制約に引っかかる可能性があり、Phase 5で使う予定の`JpaSpecificationExecutor`のような複数インターフェース実装ができなくなる
- 派生クエリ（メソッド名からの自動生成）と`@Query`は、どちらも実装コードなしでSpring Data JPAが処理してくれる。それでも表現しきれない動的条件はPhase 5の`Specification`で扱う
- `Optional.get()`を確認なしで呼ぶと`NoSuchElementException`が発生し得る。`Optional`は「存在しないかもしれない」を型で表現する仕組みなので、`.get()`で無条件に中身を取り出すとその意図を無効化してしまう。`orElseThrow`/`orElse`/Kotlinのnullable型への変換が本来の使い方
- 新規保存時の`save()`は内部で`persist()`が呼ばれ、渡したインスタンスそのものにIDが書き込まれて同一参照が返る。一方、既存エンティティの更新では`merge()`が使われ別インスタンスが返ることがあるため、「保存後は常に戻り値を使う」習慣が安全
- `./gradlew test`のコンソール出力は成功/失敗のみで、個々のテストケースの内訳は`build/test-results/test/`のXMLや`build/reports/tests/test/index.html`のHTMLレポートで確認する

## セルフチェック

- [x] `@DataJpaTest`でTodo+Category+Userの関連を保存・取得できる
- [x] DB制約・Kotlin型・JPA `optional`属性を一致させる理由を説明できる
- [x] `@OnDelete` vs JPA `CascadeType`の違いを説明できる
- [x] なぜEntityをdata classにしないかを自分の言葉で説明できる（Phase 1から持ち越し、Phase 3で解消）
- [x] `@Transactional`の基礎（伝播、ロールバック条件など）を自分の言葉で説明できる（Phase 3で解消）
- [x] 永続化コンテキスト（1次キャッシュ）が何かを自分の言葉で説明できる
- [x] `@OnDelete`（DBの外部キー制約）とHibernate自身のオブジェクトグラフ整合性チェックが別レイヤーであることを説明できる

## 関連する設計判断（ADR）

- [0001 - enumはORDINALではなくSTRINGでDB保存する](../decisions/0001-enum-storage-as-string.md)
- [0002 - Userを削除したら関連するTodo/CategoryもCASCADE削除する](../decisions/0002-cascade-delete-on-user.md)
- [0003 - Category名はユーザーごとに一意にする](../decisions/0003-category-name-unique-per-owner.md)
- [0004 - Categoryを削除したら、それに紐づくTodoのcategoryはnullにする](../decisions/0004-category-delete-sets-todo-category-null.md)
