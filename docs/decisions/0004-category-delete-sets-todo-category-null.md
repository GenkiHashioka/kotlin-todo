# 0004 - Categoryを削除したら、それに紐づくTodoのcategoryはnullにする

**ステータス**: 採用
**日付**: 2026-08-01

## Context（背景・何を解決したいか）

`Todo.category`は`ManyToOne`の任意関連（`optional`未指定＝デフォルトの`true`、`category: Category? = null`）で、TodoはCategoryが無くても成立する。この状態で「Categoryが削除されたとき、それに紐づくTodoをどう扱うか」が未決定のまま残っていた（ADR 0002はUser削除時の話のみ）。

決めないと、外部キー制約はデフォルトでは削除を拒否する挙動になり、Categoryにひとつでも紐づくTodoが残っている限りCategoryを削除できない、という曖昧な状態になる。

## Decision（何を決めたか）

`todos.category_id`の外部キーに`ON DELETE SET NULL`を設定する（`@OnDelete(action = OnDeleteAction.SET_NULL)`）。Categoryが削除されたら、それに紐づいていたTodoの`category`は自動的に`null`に更新される。TodoそのものはCASCADE削除されない。

## Consequences（この決定によって何が得られ、何を犠牲にしたか）

- **得られるもの**: Categoryは「Todoが紐づいているかどうかを気にせず自由に削除できる」ようになる。Todo自体は消えず、単に未分類（category未設定）の状態として残るため、ユーザーから見てTodoが勝手に消える事故が起きない。
- **犠牲にするもの**: Categoryを削除すると、それが何のTodoに使われていたかという情報は失われる（Todo側からは元々どのCategoryだったか復元できない）。
- **代替案として検討したもの**:
  - `RESTRICT`（Todoが紐づく限りCategory削除を禁止）: 安全だが、ユーザー体験として「未分類に戻したいだけなのにCategoryを消せない」のは不便。
  - `CASCADE`（CategoryごとTodoも削除）: Todo/User削除（ADR 0002）と違い、Categoryは「分類」に過ぎずTodo自体のライフサイクルとは無関係なため、Todoまで消えるのはユーザーの意図に反する可能性が高く却下。
- **実装上の注意**: `@OnDelete`はDBの外部キー制約（DDL）にのみ作用し、Hibernateが自分の永続化コンテキストで行うオブジェクトグラフの整合性チェックには影響しない。同一セッション内でCategoryを参照しているTodoが管理対象のまま残っていると、`TransientPropertyValueException`が発生する（`CascadeType.REMOVE`が無い関連に対する防御的エラー）。削除前に対象エンティティを永続化コンテキストから外す（`clear()`など）必要がある。
