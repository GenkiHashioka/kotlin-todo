# 設計判断記録（ADR）

実装の過程で発生した「なぜこの実装にしたか」という判断を、短く切り出して記録する。学習ジャーナル（`docs/journal/`）より粒度が細かく、後から特定の判断だけを検索しやすくするためのもの。

各ファイルはテンプレート（`TEMPLATE.md`）に沿って、番号順に追加していく。

## 一覧

| # | タイトル |
|---|---|
| 0001 | [enumはORDINALではなくSTRINGでDB保存する](0001-enum-storage-as-string.md) |
| 0002 | [Userを削除したら関連するTodo/CategoryもCASCADE削除する](0002-cascade-delete-on-user.md) |
| 0003 | [Category名はユーザーごとに一意にする](0003-category-name-unique-per-owner.md) |
| 0004 | [Categoryを削除したら、それに紐づくTodoのcategoryはnullにする](0004-category-delete-sets-todo-category-null.md) |
