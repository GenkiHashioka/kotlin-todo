# プロジェクトロードマップ

**最終更新**: 2026-09-22

このドキュメントは、`kotlin-todo`で何をどの順序で進めるかを示す。プロダクトの要件は[requirements.md](requirements.md)、現在の構成は[architecture.md](architecture.md)、Androidを優先する判断の背景は[ADR 0022](decisions/0022-prioritize-android-client.md)を参照。

## 現在の優先順位

2027年前半まではKotlin / Androidを主な学習対象とする。個人開発では、業務で経験しにくいAndroidアプリの設計・新規実装を補う。

既存Ktorバックエンドは、Androidクライアントが利用できるTodo CRUD APIとして維持する。Androidから必要になる変更や重大な不具合を除き、バックエンドのテスト戦略再構築と機能拡張は保留する。

```text
android/       主な開発対象
backend/       安定したAPI基盤
docs/          設計判断と学習過程の記録
```

## Android Track

実装は、小さなIssueとPull Requestに分けて進める。機能が必要になった時点で関連概念を学び、説明できる状態にしてから次へ進む。

- [x] Androidプロジェクトを追加する
- [x] Ktor APIからTodo一覧を取得する
- [x] `Repository → ViewModel → UI State → Compose`を通して表示する
- [x] Loading / Error / Empty Stateを扱う
- [ ] Todo詳細を表示する
- [ ] Todoを新規作成する
- [ ] Todoを編集する
- [ ] Todoを削除する
- [ ] Navigationを追加する
- [ ] DIを導入する
- [ ] ViewModel / Repository / UIのテストを追加する
- [ ] GitHub ActionsでAndroidの検証を自動実行する

この順序は固定ではない。たとえば通信失敗が開発を妨げる場合は、CRUD完了前でもError Stateを先に実装する。順序を変えるときは、問題と判断理由をIssueまたはdesign noteに残す。

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

Clean Architecture、マルチモジュール化、DIライブラリなどを、形式を整える目的だけで先に導入しない。

## Backend Track（保留中）

以下は廃止していないが、Android Trackを優先する間は保留する。

- Ktor `testApplication`によるHTTP経由の統合テスト
- DB制約の追加テスト
- Todoのフィルタ、ソート、検索、ページネーション
- Category CRUD API
- 認証と複数ユーザー対応

Androidの要件からAPI契約の変更が必要になった場合は、AndroidとBackendを同じIssueまたは関連Issueで扱う。

## 2027年前半の到達点

- Todo CRUDをAndroidから操作できる
- Loading / Error / Emptyを含む画面状態を扱える
- Navigation、DI、テストを必要性とともに説明できる
- `Compose → ViewModel → StateFlow → Repository → HTTP Client → Ktor API`をコードと図で説明できる
- 初見の人がREADMEから開発環境を起動できる
- 主要な設計判断をADRで追跡できる

## 2027年後半以降

Androidクライアントを、自分の設計・実装意図とともに第三者へ説明できる成果物として整える。バックエンド、Database、Cloudへの学習範囲拡大は、Androidを主軸にできる基礎ができた後に再判断する。
