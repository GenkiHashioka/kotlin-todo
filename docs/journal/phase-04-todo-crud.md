# Phase 4 — Todo CRUD（認証なし）

**ステータス**: 完了
**開始日**: 2026-08-01
**完了日**: 2026-08-02

## 学習目標

- DTO/Entity分離、マッピング関数
- Controller層（`@RestController`、ルーティング、`ResponseEntity`と正しいステータスコード）
- Bean Validation
- `@RestControllerAdvice`によるグローバル例外ハンドリング
- Service層（責務分離、`@Transactional`の実践投入）

## 成果物

- `dto/`（`TodoCreateRequest`, `TodoUpdateRequest`, `TodoResponse`, `CategorySummary`, `ErrorResponse` + Entity⇔DTOのマッピング拡張関数）
- `exception/`（`TodoNotFoundException`, `CategoryNotFoundException`, `GlobalExceptionHandler`）
- `service/TodoService.kt`（create/findById/findAll/update/delete）
- `controller/TodoController.kt`（Todo CRUD一式のエンドポイント）
- `config/DevDataInitializer.kt`（起動時に固定ユーザーを1人用意する`CommandLineRunner`）
- `repository/UserRepository.findByEmail`追加
- `docs/api/`（`springdoc-openapi`による自動生成OpenAPI仕様のスナップショットと説明）
- Phase 2の使い捨てコード（`HelloController`/`GreetingService`）を削除
- 既知の課題をGitHub Issue化（[#3](https://github.com/GenkiHashioka/kotlin-todo/issues/3) N+1問題、[#4](https://github.com/GenkiHashioka/kotlin-todo/issues/4) open-in-view、[#5](https://github.com/GenkiHashioka/kotlin-todo/issues/5) ロケール依存のバリデーションメッセージ、[#6](https://github.com/GenkiHashioka/kotlin-todo/issues/6) OpenAPI仕様の不正確さ）

## チェックポイント結果

- `./gradlew clean test`実行後、全テストグリーン（Phase 3の`TodoRepositoryTest`含む）
- `./gradlew bootRun`でアプリを起動し、curlで以下を確認
  - `POST /todos` → 201 Created + `Location`ヘッダー
  - `GET /todos`, `GET /todos/{id}` → 200 OK
  - `PUT /todos/{id}` → 200 OK、`updatedAt`が更新されていることを確認
  - `DELETE /todos/{id}` → 200 OK（削除内容を含む）、削除後の再取得で404
  - 空文字タイトルでのバリデーションエラー → 400 Bad Request
  - 存在しない`categoryId`指定 → 404 Not Found

## 学んだこと

### DTOとEntityの分離
- Entityをそのまま公開すると、意図しないフィールドの漏出（`Todo.owner`経由の`passwordHash`等）、LAZYな関連へのセッション外アクセス、リクエスト/レスポンスで欲しい形の不一致、といった問題が起きる
- DTOはHibernateの管理下に入らないため、Entityと違い`data class`にして良い（むしろ構造的等価性・`copy()`のメリットを享受できる）。「Hibernateに管理されるかどうか」がEntity/DTOで`data class`を使い分ける判断基準になる
- リクエストDTOの設計では「クライアントが本当に指定すべき項目か」を精査する必要がある。例えば新規作成時の`status`は、業務ルール上「常にNOT_STARTEDから始まる」ため、DTOに含めずService層で固定する方が状態遷移のルールをシンプルに保てる
- 関連Entity（`Category`等）はDTOでは実体を持たず、IDのみで表現する（`categoryId: Long?`）。逆にレスポンスでは、フロントエンドが追加のAPI呼び出しをせずに済むよう、IDだけでなく最小限の情報（`CategorySummary(id, name)`）を含めることを検討する

### マッピング関数
- Kotlinの拡張関数（`fun Todo.toResponse(): TodoResponse`）を使うと、Javaでよくある専用Mapperクラスを作らずに、自然な形でEntity→DTO変換を書ける
- Entity→DTOは単純な変換で完結するが、DTO→Entityは「クライアントが送るのはIDだけ、実際のEntity解決にはリポジトリへの問い合わせが必要」という非対称性がある。ただし、解決済みの関連Entity（`Category`/`User`）を引数として受け取る形にすれば、DTO→Entityの組み立て自体も純粋な拡張関数にできる（`TodoCreateRequest.toEntity(owner, category)`）。「IDから実体を解決する」責務だけがService層に残る
- 更新は「新しいEntityを作って返す」のではなく「既存の管理対象インスタンスのフィールドを直接書き換える」必要がある（`Todo.applyUpdate()`は`Unit`を返す）。新しいインスタンスを作ってしまうと、Hibernateが追跡している同一性が壊れる（Phase 3の「Entityをdata classにしない理由」と地続きの話）

### Controller層
- `@RestController`は`@Controller` + `@ResponseBody`。戻り値がテンプレート名ではなく、レスポンスボディそのものとして扱われる
- `ResponseEntity<T>`でボディとステータスコードをセットで表現する。エンドポイントごとに異なるステータスコード（201/200/404等）を返す必要があるため、常に200を返すデフォルト挙動に頼らない
- 201 Createdでは`Location`ヘッダーを付けるのが望ましい。`ServletUriComponentsBuilder.fromCurrentRequest()`で、現在のリクエストURLを起点に新しいリソースのURIを組み立てられる
- DELETE成功時のレスポンスには204（ボディなし）と200+削除内容、の2つの流儀がある（[ADR 0006](../decisions/0006-delete-returns-200-with-body.md)）

### Bean Validation
- `jakarta.validation.constraints`のアノテーション（`@NotBlank`, `@Size`等）をDTOのフィールドに付け、Controller側で`@Valid`を付けることで初めて検証が実行される
- Kotlinでは、アノテーションの対象を明示するため`@field:`を付ける必要がある（フィールドに実際に適用されるように指定する）
- バリデーションの制約値は、永続化層の制約（Entityの`@Column(length = ...)`）と一致させる必要がある。ズレていると、DTOの検証は通過するのにDB保存時にエラーになる、という分かりにくい失敗が起きる。これは`@Column`で`length`を省略した場合の暗黙のデフォルト値（255）にも当てはまる
- バリデーションルールは「メソッドごとに全く同じとは限らない」。例えば`dueDate`に「今日以降」という制約を検討したが、PUT方式の更新では既存の（期限切れの）値がそのまま送られてくるため、更新自体が永久に失敗するという副作用に気づき、見送った。制約を追加する際は、それが適用される全ての操作（作成・更新）でどう影響するかを考える必要がある

### `@RestControllerAdvice`による例外ハンドリング
- `@RestControllerAdvice` + `@ExceptionHandler(対象クラス::class)`で、Controller個別に`try-catch`を書かずに、アプリ全体で例外処理を一箇所に集約できる
- `MethodArgumentNotValidException`（`@Valid`失敗時にSpringが自動で投げる）の`bindingResult.fieldErrors`から、フィールドごとのエラーメッセージを組み立てられる。`toString()`をそのまま使うとSpring内部の実装詳細が漏れるため、必要な情報（`field`/`defaultMessage`）だけを取り出して整形する必要がある
- Bean Validationのデフォルトエラーメッセージは、リクエストの`Accept-Language`（無ければOS/JVMのロケール）に応じて自動的にローカライズされる。今回はこれを既知の課題として残した（Issue #5）

### Service層
- Controllerに業務ロジック（IDからEntityを解決する、業務ルールに基づくデフォルト値を決める等）を書かない。Service層に集約することで、責務分離・トランザクション境界の設定・テストのしやすさ・再利用性が得られる
- `findById`/`findAll`のような参照系には`@Transactional(readOnly = true)`を付け、ダーティチェックの省略や意図の明示を行う
- `update`メソッドでは、`applyUpdate()`でフィールドを書き換えた後、`saveAndFlush()`で明示的にflushする必要がある。理由は「DBへの反映を確定させるため」ではなく（`@Transactional`メソッド内の変更は、明示的な`save()`が無くてもコミット時に自動でUPDATEされる）、`@LastModifiedDate`（`updatedAt`）がflush時にしか発火しないため、レスポンスとして返す値を正しくするために必要、という点
- `jakarta.transaction.Transactional`（JTA標準、機能が限定的）と`org.springframework.transaction.annotation.Transactional`（Spring独自、`rollbackFor`等の豊富な機能を持つ）は別物で、IDEの自動importで誤って前者が選ばれることがある。JPA本体のアノテーション（`jakarta.persistence.*`）やBean Validation（`jakarta.validation.*`）には元々「Spring版」が存在しないため混同のしようがなく、この問題は実質`@Transactional`だけの特殊事情

### Kotlinの言語機能
- `const val`は「コンパイル時に値が確定する定数」で、トップレベルか`object`/`companion object`の直下にしか書けない。Javaの`public static final`に近い
- `companion object`は「特定のクラスに紐づいた、ただ1つのシングルトンオブジェクト」。Kotlinには`static`キーワードが無く、クラスレベルのメンバーはこの仕組みで表現する
- Singletonデザインパターン（private constructor + static getInstance()）を、Kotlinの`object`宣言が言語機能として肩代わりしてくれる。Spring Beanのデフォルトスコープ（Singleton）も、DIコンテナが同じ考え方で1インスタンスを使い回している

### 実運用に関わる既知の課題（今回は対応を見送り、Issue化）
- **N+1問題**（[#3](https://github.com/GenkiHashioka/kotlin-todo/issues/3)）: `findAll`で各Todoの`category`（LAZY）にアクセスするたび追加SELECTが発行される。`@OnDelete`と同様、LAZY/EAGERの選択そのものはN+1の原因ではなく、「JOIN FETCHのような最適化を伴わずに関連を辿るコードを書いたこと」が原因。EAGERにしても解決しない
- **Open Session In View**（[#4](https://github.com/GenkiHashioka/kotlin-todo/issues/4)）: `spring.jpa.open-in-view`が有効だと、HibernateセッションがController層まで開いたままになり、「マッピングはService層のトランザクション内で行うべき」という原則を破っても表面上動いてしまう
- **springdoc-openapiの自動生成の限界**（[#6](https://github.com/GenkiHashioka/kotlin-todo/issues/6)）: 実行時に動的に決まるステータスコード（`ResponseEntity.created()`の201等）や、`@RestControllerAdvice`で処理しているエラーレスポンスは、静的解析だけでは仕様書に反映されない。`@ApiResponses`等の追加アノテーションが必要

## セルフチェック

- [x] Entityをそのまま公開せず、DTOで分離する理由を説明できる
- [x] Kotlin拡張関数を使ったEntity⇔DTOマッピングの書き方と、方向による非対称性を説明できる
- [x] `@RestController`/`ResponseEntity`を使い、エンドポイントごとに適切なステータスコードを返せる
- [x] Bean Validationの制約を、永続化層の制約と整合させる必要性を説明できる
- [x] `@RestControllerAdvice`でグローバルな例外ハンドリングを実装できる
- [x] Service層の責務（Controller/Repositoryとの違い）と、`@Transactional`の実践的な使い分け（`readOnly`、flushのタイミング）を説明できる
- [x] `jakarta.transaction.Transactional`とSpring版`@Transactional`の違いを説明できる
- [x] `companion object`/Singletonパターンの仕組みを説明できる
- [x] N+1問題がLAZY/EAGERどちらでも起こりうる理由を説明できる

## 関連する設計判断（ADR）

- [0005 - 更新はPUT形式（全項目送信）とし、PATCH方式は採用しない](../decisions/0005-update-uses-put-not-patch.md)
- [0006 - DELETE成功時は204ではなく200+削除内容を返す](../decisions/0006-delete-returns-200-with-body.md)
- [0007 - 認証機能ができるまでは固定ユーザーで代用する](../decisions/0007-fixed-user-until-auth-exists.md)
