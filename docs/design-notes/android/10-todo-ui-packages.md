# 詳細設計メモ - Android 10: Todo UIを画面別パッケージへ整理する

**バージョン**: 0.1
**作成日**: 2026-10-18
**対応Issue**: [#58](https://github.com/GenkiHashioka/kotlin-todo/issues/58)

## 1. 背景

Todo CRUDと成功フィードバックが揃い、ui/todoには一覧・詳細・登録・編集の4画面分、計20ファイルが集まっている。各画面のRoute、Screen、UiState、ViewModel、Factoryを近くに配置し、変更時に関連ファイルと影響範囲を把握しやすくする。

UI・状態管理・Repository・通信境界の責務は既に分離されている。今回の課題は、同じパッケージ内で画面をまたいだファイルが並び、関連ファイルを探す必要があること。画面別に配置を整理し、既存の処理を維持する。

関連文書: [基本設計](../../architecture.md)、[要件](../../requirements.md)、[ロードマップ](../../roadmap.md)、[ADR 0009](../../decisions/0009-monorepo-structure.md)、[ADR 0022](../../decisions/0022-prioritize-android-client.md)、[ADR 0023](../../decisions/0023-navigation-refresh-notification.md)、[Android 09のjournal](../../journal/android/09-todo-success-feedback.md)。

## 2. スコープ

### 今回扱うもの

- ui/todoの20ファイルを、list / detail / create / editのサブパッケージへ移動する。
- package宣言と参照元のimportを更新する。
- 各画面の責務と既存の依存関係を確認する。
- ビルドと既存の主要操作を確認し、基本設計のディレクトリ図などを実装に合わせる。

### 今回扱わないもの

クラス・関数の改名、引数や処理内容の変更、フォームの共通化、新しいUI部品の抽出、domain層やUseCaseの追加、DTOの置き換え、DIライブラリ、Gradleモジュール分割、Navigation引数や再取得通知の変更、Backend変更、自動テスト基盤の導入。

移動と参照の修正以外が必要になった場合は、問題と影響を説明してから範囲を判断する。

## 3. 設計方針

### 3.1 画面ごとに関連ファイルをまとめる

採用する配置は次のとおり。波括弧は5ファイルを省略して表記したもの。

```text
ui/
├── navigation/
│   ├── KotlinTodoNavHost.kt
│   └── TodoDestinations.kt
├── todo/
│   ├── list/
│   │   └── TodoList{Route,Screen,UiState,ViewModel,ViewModelFactory}.kt
│   ├── detail/
│   │   └── TodoDetail{Route,Screen,UiState,ViewModel,ViewModelFactory}.kt
│   ├── create/
│   │   └── TodoCreate{Route,Screen,UiState,ViewModel,ViewModelFactory}.kt
│   └── edit/
│       └── TodoEdit{Route,Screen,UiState,ViewModel,ViewModelFactory}.kt
└── theme/
```

例えばTodoListViewModelの移動先はui/todo/list/TodoListViewModel.kt、packageはcom.genkihashioka.kotlintodo.ui.todo.listとする。他の画面も同じ対応にする。

| 選択肢 | 利点 | 今回の判断 |
|---|---|---|
| ui/todoへ平置きを継続 | 階層が浅い | 4画面分が混在するため見直す |
| 画面別に5ファイルずつまとめる | 1画面の変更を同じ場所で追える | 採用 |
| route / screen / viewmodel / state / factory別にまとめる | 同じ種類のファイルを比較しやすい | 1画面の変更で複数の場所を行き来するため今回は採用しない |

各画面の下にさらにpresentationやviewmodelなどの階層は作らない。5ファイルの段階では探索を増やすため。既存のTodoListなどの名前も、NavHostのimportや検索で画面を識別できるため維持する。

削除は詳細画面の操作であり、独立した画面を持たない。deleteパッケージは作らず、詳細の5ファイルに含める。

### 3.2 配置と責務・依存関係を区別する

| 要素 | 継続する責務 |
|---|---|
| Route | ViewModelの状態監視、Screenへのcallback接続、成功状態からNavHostへの通知 |
| Screen | 状態に応じた描画、入力・確認ダイアログ・処理中の操作制御 |
| UiState | 画面に必要な値と処理状態の表現 |
| ViewModel | 入力・処理状態の管理、Repository呼び出し、結果の状態への変換 |
| Factory | Repositoryと、必要な場合のSavedStateHandleをViewModelへ渡す |

現状、各画面のRouteは同じ画面のScreenとViewModelを使い、ViewModelはRepositoryを呼ぶ。詳細・編集ViewModelがDestinationからIDを読む既存の参照も維持する。画面同士でRouteやViewModelを直接参照する構成は追加しない。

MainActivityのFactory関数4つのimport、NavHostのRoute・ViewModel計8つのimportは移動先へ更新する。各画面内の型はまとめて同じパッケージへ移す。Repository、通信モデル、R、NavigationのDestinationの配置は変更しない。

パッケージ分割だけで新しいレイヤーやモジュールの境界ができるわけではない。今回改善するのは配置と探索性であり、呼び出し関係・状態更新・Coroutineの寿命は既存のままとする。

### 3.3 共通機能と文書の扱い

Navigationは複数画面をつなぐためui/navigationへ、テーマはui/themeへ残す。Snackbarの状態・Scopeと共通ScaffoldもMainActivityへ残す。単に複数画面が利用することだけを理由にcommonやutilパッケージを追加しない。

実装後は基本設計のディレクトリ図と配置方針を更新し、journal 10へ結果を記録する。README・文書一覧・ロードマップは影響箇所を更新する。プロダクトの振る舞いは変えないため要件の変更は原則不要だが、PR前に整合を確認する。過去のdesign-noteとjournalの旧パスは当時の記録として維持する。

画面別の配置は今後の追加にも適用する方針になるため、実装結果を踏まえて新規ADRへの記録を検討する。既存のモノレポ・成功後再取得のADRは継続する。

## 4. 実装ステップ

1. Windows側でrefactor/android-todo-ui-packagesブランチを作成し、設計をレビューする。
2. 一覧の5ファイルをlistへまとめて移動し、packageとMainActivity / NavHostの参照を更新する。Android StudioのRefactor / Moveを使い、変更対象を確認する。
3. 一覧の移動差分をレビューし、Android Studioでビルド・一覧表示・詳細への遷移を確認する。
4. 同じ方針で詳細、登録、編集を1画面ずつ移し、差分を確認する。
5. 旧パッケージ参照の残存と処理内容の変更がないことを確認し、全体ビルドと主要操作の確認を行う。
6. journalと基本設計などを更新し、ADRの要否を判断する。
7. 開発者が文書をレビューし、コミット・push・PR作成を行う。

実装は開発者が行い、Codexは移動先と参照の整合、責務と振る舞いを保てているかをレビューする。

## 5. 確認方法

以下は予定であり、実施済みではない。実行確認は開発者の報告、静的確認はCodexの結果としてjournalへ分けて記録する。

### 静的確認

- 4サブパッケージに各5ファイル、計20ファイルが揃う。
- ディレクトリとpackageが対応し、MainActivity / NavHostが移動先を参照する。
- ソース内に旧ui.todo直下の型・Factoryへのimportが残っていない。
- ファイルの移動とpackage / importの更新以外に処理の差分がない。Gitのrename検出による表示とファイル内容の比較を併用する。
- git diff --checkと文書のリンク確認が通る。

### ビルド・手動確認

- Android Studioでビルドし、アプリを起動できる。
- 一覧から詳細、詳細から編集、一覧から登録へ遷移でき、各画面から戻れる。
- 登録・編集・削除が成功し、戻り先のデータ更新と対応するSnackbarを確認できる。
- 削除確認のキャンセルで詳細が維持される。

今回の配置変更に対する主要経路の確認と、実装が一通り揃った段階で行う機能横断の回帰テストは区別する。通信失敗・タイムアウト・処理中の操作制御などを網羅する手順と自動化の範囲は、別途テスト計画で整理する。既存のAndroid自動テスト用ソースは現時点では存在せず、今回移動するテストファイルはない。

## 6. 想定される詰まりポイント

- ファイルを移しただけでpackage宣言が旧位置のままになる。移動先との対応を確認する。
- MainActivityやNavHostのimportが更新されず参照できなくなる。IDEの移動プレビューとビルドで確認する。
- 同じパッケージだった型を一部だけ移すと一時的に参照が解決しない。同じ画面の5ファイルをまとめて移す。
- Project表示のパッケージ省略・結合表示で階層が見えにくい場合がある。実ファイルのパスとpackage宣言も確認する。
- Gitが移動を削除・新規追加として表示しても、それだけで内容が失われたとは限らない。rename検出と内容の比較で確認する。
- 移動のついでに改名や共通化を混ぜると、挙動維持のレビューが難しくなる。必要性が生じた場合は別の変更として相談する。
