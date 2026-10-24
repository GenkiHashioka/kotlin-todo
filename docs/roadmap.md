# プロジェクトロードマップ

**最終更新**: 2026-10-24

このドキュメントは、`kotlin-todo`で何をどの順序で進めるかを示す。プロダクトの要件は[requirements.md](requirements.md)、現在の構成は[architecture.md](architecture.md)、Androidを優先する判断の背景は[ADR 0022](decisions/0022-prioritize-android-client.md)を参照。

## 現在の優先順位

2026-10-24の学習方針の見直しを受け、Kotlin / Androidの専門性を軸に、プロダクトを設計・実装・説明・公開・運用できる力を段階的に伸ばす。実装と検証の根拠を積み重ね、必要な領域を順に学ぶ。

この文書には技術学習とプロダクト開発に関わる方針を残す。未確認の成果を補完せず、計画書の未チェック項目だけで、コード・検証記録に基づく進捗を未実装・未確認へ戻さない。

既存Ktorバックエンドは、Androidクライアントが利用できるTodo CRUD APIとして維持する。Androidから必要になる変更や重大な不具合を除き、バックエンドのテスト戦略再構築と機能拡張は保留する。

```text
android/       主な開発対象
backend/       安定したAPI基盤
docs/          設計判断と学習過程の記録
```

## KotlinTodoの役割と次の区切り

KotlinTodo（リポジトリ名はkotlin-todo）は、UI・操作性の改善を一つの区切りとし、その後も学習用サンドボックスとして維持する。現場や次のプロダクトで必要になった状態管理・DI・テスト等を小さく比較・検証し、目的・結果・採否を残す。実験と安定した状態を区別する。

直近の進め方は次のとおり。

1. 既存CRUD・構成・手動回帰テストの記録を土台に現在地を把握する。根拠のある完了項目は作り直さず、未実装・未検証・改善候補を区別する。
2. UI改善の対象画面・困りごと・完了条件と対象外を決め、Issueと設計へ落とし込む。現在もComposeを使っているため、「近代化」を技術の全面置換とは解釈しない。
3. 一覧ViewModelの自動テスト案は候補として維持し、UI変更のリスクと学習上の必要性を踏まえて、今回先に行う範囲を決める。全画面の自動化・DIライブラリ・CI整備を一律に主力移行の必須条件にしない。
4. 開発者が改善結果・検証・説明を確認して区切りを判断し、残課題をBacklogへ分ける。改善項目を増やし続けて区切りを延ばさない。
5. 主力の個人開発をSkySubaruへ移し、KotlinTodoは必要な実験に絞って続ける。

SkySubaruは2027年のAndroid版公開と自作バックエンドを目標とする別プロダクトである。公開・運用に必要なBackend・DB・クラウドは年だけを理由に延期せず、要件から順番に学ぶ。その後iOS / Webへの展開を検討する。機能・公開月・フレームワーク・クラウド・共通化方式は本リポジトリでは確定しない。SkySubaruの構想を、そのままKotlinTodoの追加機能にはしない。

## Android Track

実装は、小さなIssueとPull Requestに分けて進める。機能が必要になった時点で関連概念を学び、説明できる状態にしてから次へ進む。

- [x] Androidプロジェクトを追加する
- [x] Ktor APIからTodo一覧を取得する
- [x] `Repository → ViewModel → UI State → Compose`を通して表示する
- [x] Loading / Error / Empty Stateを扱う
- [x] ローカルAPIベースURLをGit管理対象のコードから分離する
- [x] Todo詳細を表示する
- [x] Todoを新規作成する（実装・手動確認済み）
- [x] Todoを編集する（実装、保存成功・失敗と送信中の入力制御を手動確認。個別の確認範囲は[Android 06のjournal](journal/android/06-todo-edit.md)参照）
- [x] Todoを削除する（[Issue #54](https://github.com/GenkiHashioka/kotlin-todo/issues/54)。削除中の戻る制御とタイムアウトを含め実装・手動確認。範囲と制約は[Android 07のjournal](journal/android/07-todo-delete.md)参照）
- [x] 登録・編集の保存中も戻る操作を抑止する（[Issue #57](https://github.com/GenkiHashioka/kotlin-todo/issues/57)。送信中表示と登録フォームの入力制御を含め実装・手動確認。[journal](journal/android/08-todo-submit-navigation.md)参照）
- [ ] タイムアウト・レスポンス喪失時の結果確認と整合回復を設計する（エラー文言、既に削除済みの場合の操作も含む）
- [x] 登録・編集・削除の成功結果をSnackbarで明示する（[Issue #55](https://github.com/GenkiHashioka/kotlin-todo/issues/55)、[設計メモ](design-notes/android/09-todo-success-feedback.md)、実装・手動確認の範囲は[journal](journal/android/09-todo-success-feedback.md)参照）
- [x] `ui/todo`を画面別のlist / detail / create / editへ整理する（[Issue #58](https://github.com/GenkiHashioka/kotlin-todo/issues/58)。責務と振る舞いを維持し、主要経路を手動確認。[設計メモ](design-notes/android/10-todo-ui-packages.md)、[journal](journal/android/10-todo-ui-packages.md)、[ADR 0025](decisions/0025-screen-based-todo-ui-packages.md)参照）
- [x] Navigationを追加する
- [ ] DIを導入する
- [x] 機能横断の手動回帰テストを設計・実施する（[Issue #62](https://github.com/GenkiHashioka/kotlin-todo/issues/62)。[テスト設計](test-plans/android/11-todo-regression-testing.md)、[journal](journal/android/11-todo-regression-testing.md)、[実施記録](testing/android/todo-regression-results.md)参照）
- [ ] 一覧ViewModelの状態遷移・retry / refreshをローカル単体テストする（[Issue #63](https://github.com/GenkiHashioka/kotlin-todo/issues/63)。UI改善の区切りと併せて着手順を決める）
- [ ] UI・操作性の改善対象と完了条件を決め、実装・検証して一区切りにする
- [ ] ViewModel / Repository / UIのテストを追加する
- [ ] GitHub ActionsでAndroidの検証を自動実行する

CRUD実装後の保存中の戻る制御（#57）、成功フィードバック（#55）、画面別パッケージ整理（#58）を実装した。手動回帰テストと検証用コード除去後の通常構成での最終確認を実施した。一覧ViewModelのローカル単体テストは最初の自動化候補として残し、次の区切りに必要なUI改善と検証の範囲を決める。

手動回帰ではTodo CRUD・成功通知・失敗後の復帰・処理中の操作制御・操作成功後の取得失敗を確認し、タイムアウト時の結果不明も観察した。全端末・全条件や、自動テストで検証したという意味ではない。後続では、入力変換・送信ガード・Compose UI・画面連携も必要性に応じて段階的に自動化する。

この順序は固定ではない。たとえば通信失敗が開発を妨げる場合は、CRUD完了前でもError Stateを先に実装する。順序を変えるときは、問題と判断理由をIssueまたはdesign noteに残す。

後続の改善として、フォームのレイアウト・ラジオボタンの行全体の選択操作・一覧の並び順を扱う。ポートフォリオのUI仕上げでは、一覧のTodoがタップ可能だと伝わる見た目も改善する。登録・編集のタイトルを1行入力とする案も検討する。長いタイトルの表示上の折り返しと、入力・貼り付け・既存データに含まれる改行の扱いを分けて設計する。現在の一覧APIには明示的な並び順がなく、更新後の再取得で順序が変わり得る。ソート方針とAPI・Androidの責務は別課題で設計する。

## 学習上の判断基準

新しい技術や抽象化は、現在の問題を解決するときに追加する。

| 問題 | 学ぶ・導入する概念 |
|---|---|
| API通信を画面から分離したい | Repository |
| 非同期処理の結果を画面へ反映したい | Coroutines / StateFlow |
| 画面状態を一方向に流したい | ViewModel / UI State / UDF |
| 画面が増えた | Navigation Compose |
| 依存関係の生成や差し替えが複雑になった | DI |
| 変更による回帰が怖くなった | Unit Test / UI Test |

Clean Architecture、マルチモジュール化、DIライブラリなどを、形式を整える目的だけで先に導入しない。新しい学習は、Androidの専門性・具体的なプロダクトの公開運用・設計や品質の改善のどこに役立つかと、今必要かを判断する。不要な並行学習を避け、後回しにする場合は着手条件を残す。

英語は今から継続する。開発で必要な公式文書を読む、任意で短い英語の要約を書いてみるなど、実際の作業と結び付ける。説明・レビューは引き続き日本語を基本とし、英語学習を理由に理解の確認を省かない。固定の学習時間や週次成果を義務にせず、開発に使える時間と無理なく継続できる量に調整する。月次・四半期やプロダクトの節目に、本人の理解・作業量・主力開発の進捗を踏まえて見直す。

## Backend Track（保留中）

以下は廃止していないが、Android Trackを優先する間は保留する。

- Ktor `testApplication`によるHTTP経由の統合テスト
- DB制約の追加テスト
- Todoのフィルタ、ソート、検索、ページネーション
- Category CRUD API
- 認証と複数ユーザー対応

Androidの要件からAPI契約の変更が必要になった場合は、AndroidとBackendを同じIssueまたは関連Issueで扱う。

## 学習上の到達目標

以下は継続して伸ばす能力の目安であり、すべてをUI改善の区切りやSkySubaruへの移行の必須条件にはしない。日付だけで達成と判断せず、実装・検証・本人の説明を根拠に確認する。

- Todo CRUDをAndroidから操作できる
- Loading / Error / Emptyを含む画面状態を扱える
- Navigation、DI、テストを必要性とともに説明できる
- `Compose → ViewModel → StateFlow → Repository → HTTP Client → Ktor API`をコードと図で説明できる
- 初見の人がREADMEから開発環境を起動できる
- 主要な設計判断をADRで追跡できる

## 中長期の見直し

Androidの専門性と、SkySubaruを公開・運用する経験を組み合わせ、Backend・Database・Cloud・Architectureへ理解を広げる。2027年の公開は目標であり、機能・方式・期限は実装状況と生活上の負荷を踏まえて見直す。成果は、自分で設計・実装・説明・改善できる範囲と、その検証結果で判断する。
