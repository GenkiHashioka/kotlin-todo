# 詳細設計メモ - Android 03: ローカルAPIベースURLを環境ごとに設定する

**バージョン**: 1.0
**最終更新**: 2026-09-22
**対応Issue**: #43

## 1. 背景

Androidクライアントは、Windows上のAndroid EmulatorからWSL2上のKtor APIへ接続している。現在はWSL2のIPアドレスを`ApiClient.kt`へ直接記述しているため、WSL2の再起動などでIPアドレスが変わるたびにKotlinコードの修正が必要になる。

環境固有の値がGit管理対象のアプリケーションコードへ含まれていることも問題である。Issue #43では、開発者固有のAPIベースURLをローカル設定へ移し、Gradleのビルド設定を通してAndroidアプリへ渡す。

## 2. スコープ

### このPRで作るもの

- Git管理外の場所でAPIベースURLを設定する仕組み
- GradleからAndroidアプリへ設定値を渡す`BuildConfig`フィールド
- `BuildConfig.API_BASE_URL`を利用する`ApiClient`
- 設定値がない場合に、原因を判断できるビルドエラー
- Windows / WSL2環境での設定手順

### このPRで作らないもの

- 本番環境用のAPIベースURL
- build typeやproduct flavorごとの接続先切り替え
- CI上の接続先設定
- WSL2のIPアドレス固定化
- Backendのホスト・ポート変更
- HTTPS対応
- 認証情報や秘密情報の管理

## 3. 設計方針

### 3.1 設定値の保存場所とアプリへの受け渡しを分ける

今回必要な役割は2つある。

```text
保存場所
開発環境ごとのAPIベースURLをGit管理外に置く

受け渡し
Gradleが設定値を読み、Androidコードから参照できる形にする
```

`BuildConfig`は設定値の保存場所ではない。Gradleが読み取った値から生成され、コンパイル後のAndroidコードへ値を渡す役割を持つ。

### 3.2 設定場所の比較

| 候補 | 利点 | 問題 | 判断 |
|---|---|---|---|
| `local.properties` | Android Studioが生成し、通常はGit管理外 | Android公式ではAndroid Gradle Plugin固有の値に予約されており、独自値は別ファイルが推奨されている | 不採用 |
| Project内の`gradle.properties` | Gradleから簡単に読める | 現在Git管理されており、開発者固有値を入れると共有される | 不採用 |
| Gradle User Homeの`gradle.properties` | Git管理外で、Gradle標準のProject Propertyとして読める。Android Studioのビルドでも利用できる | プロジェクト外にあるため、設定場所をREADMEで明示する必要がある | **採用** |
| 専用の`api.properties` | プロジェクト単位で分かりやすい | ファイルの手動読み込み、ignore設定、サンプルファイルの管理が追加で必要 | 代替案 |
| debug用resource | Androidコードから参照しやすい | 環境固有値の保存場所を別途用意する必要があり、URLをUI resourceとして扱う理由も弱い | 不採用 |

Windows側のGradle User Homeにある次のファイルへProject Propertyを設定する。

```properties
# %USERPROFILE%\.gradle\gradle.properties
kotlinTodoApiBaseUrl=http://<WSL2_IP>:8080/
```

プロパティ名は、他のGradleプロジェクトと衝突しにくいように`kotlinTodoApiBaseUrl`とする。

### 3.3 設定値の流れ

```text
%USERPROFILE%\.gradle\gradle.properties
    ↓ providers.gradleProperty("kotlinTodoApiBaseUrl")
android/app/build.gradle.kts
    ↓ Android Gradle Plugin Variant API
BuildConfig.API_BASE_URL
    ↓
ApiClient
    ↓ Retrofit.Builder().baseUrl(...)
Ktor API
```

Gradleでは`providers.gradleProperty()`を使う。GradleのProvider APIを通すことで、Project Propertyの標準的な取得経路を利用する。

### 3.4 `BuildConfig`を使う

AGP 9では、カスタム`BuildConfig`フィールドを追加するために次の設定が必要になる。

- `buildFeatures.buildConfig = true`で生成を有効にする
- 公開されたVariant APIで`BuildConfigField`を追加する
- `String`値を生成コードへ埋め込むため、値そのものを二重引用符で囲む

生成するフィールド名は、Kotlinの定数名として読みやすい`API_BASE_URL`とする。現在はローカル開発だけを対象としているため、設定した値を現在のbuild variantへ共通して渡す。本番環境が必要になった時点で、debug / releaseまたはproduct flavorごとの設定へ分ける。

APIベースURLは秘密情報ではない。ただし`BuildConfig`の値はAPKへ含まれるため、将来APIキーやパスワードを同じ方法で保存してはいけない。

### 3.5 設定不足をビルド時に検出する

設定値がない場合に仮のURLへフォールバックすると、ビルドは成功しても実行時の通信失敗まで原因が分からない。`kotlinTodoApiBaseUrl`が未設定なら、設定場所とプロパティ名を示してビルドを失敗させる。

RetrofitのベースURLには末尾の`/`が必要である。空文字と末尾`/`の不足もGradle設定時に検出し、実行時の`IllegalArgumentException`より前に原因を示す。

## 4. 実装ステップ

1. Gradle User Homeの`gradle.properties`へ`kotlinTodoApiBaseUrl`を設定する
2. `android/app/build.gradle.kts`で`BuildConfig`生成を有効にする
3. Provider APIでProject Propertyを読み、Variant APIから`API_BASE_URL`を生成する
4. 設定不足とURL末尾の`/`を検証する
5. `ApiClient.kt`の固定IPを`BuildConfig.API_BASE_URL`へ置き換える
6. READMEへ設定方法とWSL2のIP確認方法を記載する
7. Gradle Sync、debug build、Emulatorからの接続を確認する
8. URL変更時にKotlinコードの差分が生じないことを確認する

## 5. 確認方法

### 設定

PowerShellでWSL2のIPアドレスを確認する。

```powershell
wsl -d Ubuntu -- hostname -I
```

WindowsユーザーのGradle設定へ、末尾`/`を含むURLを記述する。

```properties
kotlinTodoApiBaseUrl=http://<WSL2_IP>:8080/
```

### ビルドと動作確認

```powershell
cd android
.\gradlew.bat :app:assembleDebug
```

- Gradle Syncとdebug buildが成功する
- Backend起動中、EmulatorでTodo一覧が表示される
- Backend停止中、既存のError画面が表示される
- 設定値を変更してSync / rebuildしたとき、`ApiClient.kt`に差分が生じない
- `ApiClient.kt`とGit管理対象ファイルに開発者固有のWSL2 IPが残っていない

### 設定不足の確認

一時的に`kotlinTodoApiBaseUrl`を削除またはコメントアウトし、設定方法を示すエラーでビルドが失敗することを確認する。確認後は設定を元へ戻す。

## 6. 想定される詰まりポイント

- `local.properties`はGit管理外だが独自プロパティ用ではないため、API URLを書かない
- Android StudioはGradle設定変更後にSyncが必要になる
- AGP 8以降は`BuildConfig`生成が既定で無効なため、明示的に有効化する
- `BuildConfigField`の`String`値には、生成コード用の二重引用符も含める必要がある
- RetrofitのベースURLは末尾`/`がないと実行時エラーになる
- User Homeの`gradle.properties`は他プロジェクトからも読めるため、衝突しにくいプロパティ名を使う
- Gradle設定を変えただけではインストール済みアプリの値は変わらないため、Sync / rebuild / 再実行する

## 7. 参考資料

- [Android Developers: Configure your build](https://developer.android.com/build)
- [Android Developers: BuildConfig](https://developer.android.com/agents/skills/build-system/agp/agp-9-upgrade/references/buildconfig)
- [Gradle: Build Environment Configuration](https://docs.gradle.org/current/userguide/build_environment.html)
