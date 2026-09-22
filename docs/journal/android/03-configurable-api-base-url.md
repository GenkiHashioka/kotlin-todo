# Android 03 - ローカルAPIベースURLを環境ごとに設定する

**ステータス**: 完了
**完了日**: 2026-09-22
**対応Issue**: #43
**設計メモ**: [Android 03: ローカルAPIベースURLを環境ごとに設定する](../../design-notes/android/03-configurable-api-base-url.md)

## 目的

`ApiClient.kt`へ直接記述していたWSL2のIPアドレスをGit管理対象のコードから分離する。GradleのProject Property、Provider API、Android Gradle PluginのVariant API、`BuildConfig`を通じて、ビルド設定からAndroidコードへ値が渡る流れを理解する。

## 作成したもの

- Gradle User Homeの`gradle.properties`で管理する`kotlinTodoApiBaseUrl`
- Project Propertyを読み取って検証する`Provider<String>`
- Variantごとに生成する`BuildConfig.API_BASE_URL`
- `BuildConfig.API_BASE_URL`を使用する`ApiClient`
- 未設定と末尾`/`不足を区別するビルド時検証
- READMEのローカル接続設定手順

## 学んだこと

### 保存場所と受け渡し方法は別の責務

Gradle User Homeの`gradle.properties`は、開発環境固有の値をGit管理外へ保存する。`BuildConfig`は保存場所ではなく、その値をビルド時にAndroidコードへ渡すための生成コードである。

```text
Gradle User Homeのgradle.properties
    ↓
android/app/build.gradle.kts
    ↓
BuildConfig.API_BASE_URL
    ↓
ApiClient
    ↓
Retrofit
```

`local.properties`もGit管理外だが、Android公式ではAndroid Gradle Plugin固有の設定用に予約されているため、独自のAPI URLは保存しない。

### Provider APIと`map`

`providers.gradleProperty("kotlinTodoApiBaseUrl")`は、設定値を`Provider<String>`として取得する。Providerは値をすぐ取り出すのではなく、必要になったときに提供するGradleの仕組みである。

`map`の中で未設定と末尾`/`を検証し、最後に`apiBaseUrl`を返す。Kotlinのlambdaは最後の式を戻り値とするため、この行がなければ`require`の戻り値である`Unit`が返り、`Provider<String>`を維持できない。

### 設定ミスを早く検出する

未設定時に仮のURLへフォールバックすると、実行時の通信失敗まで原因が分からない。今回は次をビルド時に検出する。

```text
設定なし       → kotlinTodoApiBaseUrl is not set
末尾/なし      → kotlinTodoApiBaseUrl must end with '/'
BuildConfig無効 → BuildConfig generation must be enabled
```

`buildConfigFields`はnullableだが、セーフコールを使うとnullの場合にフィールド生成を黙って省略する。今回は`BuildConfig`が必須なので、`requireNotNull`で設定の矛盾をその場で検出する。

### `BuildConfigField`は生成するソースコードを表す

`BuildConfigField`の`value`は、完成したStringとして自動的に引用されるのではなく、生成コードへそのまま書き込まれる。`String`のJavaコードを生成するため、値に二重引用符を含める必要がある。

```kotlin
value = "\"$apiBaseUrl\""
```

```java
public static final String API_BASE_URL = "http://<WSL2_IP>:8080/";
```

### ビルド時の設定である

`BuildConfig.API_BASE_URL`はアプリ実行中に`gradle.properties`を読む仕組みではない。Gradleがビルド時に生成し、値をAPKへ組み込む。そのため、URLを変更した場合はSync / rebuildが必要になる。

GitHubへ環境固有のIPは含まれないが、APKを解析すれば値を確認できる。API URLには利用できるが、APIキーやパスワードなどの秘密情報を隠す方法にはならない。

## 動作確認

- `gradlew.bat properties --property kotlinTodoApiBaseUrl -q`で設定値を確認
- 正しい設定で`:app:assembleDebug`が成功することを確認
- 設定をコメントアウトし、未設定用メッセージでbuildが失敗することを確認
- URL末尾の`/`を外し、形式不正用メッセージでbuildが失敗することを確認
- 正しい設定へ戻し、再度buildが成功することを確認
- Backend起動中、Android EmulatorでTodo一覧が表示されることを確認
- `ApiClient.kt`とGit管理対象ファイルから開発者固有のIPが消えていることを確認

## 実装中に見直したこと

最初はnullableな`buildConfigFields`へセーフコールで`put()`していた。レビューで、nullの場合にフィールド生成が黙って省略され、原因から離れた場所でエラーになることを確認した。`BuildConfig`は今回の必須条件なので、`requireNotNull`へ変更した。

## design noteとの差分

設計どおり、Gradle User Home、Provider API、Variant API、`BuildConfig`を利用した。未設定、末尾`/`不足、`buildConfigFields`のnullをそれぞれ早い段階で検出する構成も実装した。

IPアドレスの自動検出は追加していない。自動化するとGradle buildがWindowsとWSL2の起動状態へ依存するため、現在はIP変更時にGradle User Homeの値を手動で更新する。

## 現在の制約

- WSL2のIPが変わった場合は設定値の手動更新が必要
- 設定変更後はSync / rebuildが必要
- build typeやproduct flavorごとの接続先は分けていない
- 本番環境用の接続先は定義していない
- CI上での設定方法は定義していない

## 理解度確認

- [x] Gradle User Home、`BuildConfig`、`ApiClient`の役割の違い
- [x] `Provider<String>`と`map`内で値を返す理由
- [x] 未設定と末尾`/`不足をビルド時に検出する理由
- [x] `BuildConfigField`のString値に引用符が必要な理由
- [x] セーフコールではなく`requireNotNull`を使う理由
- [x] IP変更後にSync / rebuildが必要な理由
- [x] `BuildConfig`を秘密情報の保存に使えない理由
- [x] `local.properties`を独自設定に使わなかった理由

Issue #43の設定値の流れ、失敗時の挙動、採用理由と制約を、自分の言葉で説明できることを対話で確認した。
