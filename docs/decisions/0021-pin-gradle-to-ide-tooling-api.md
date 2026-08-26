# 0021 - Gradle のバージョンを IntelliJ IDEA 同梱の Tooling API に合わせる

**ステータス**: 採用
**日付**: 2026-08-25

## Context（背景・何を解決したいか）

Phase 4.10 の作業中、IntelliJ IDEA で Gradle 同期が成立しなくなった。症状は次のとおり。

| | |
|---|---|
| 同期の記録 | `resolution task executed`（**成功として記録される**） |
| 取り込まれたモジュール | **0 件**（`Update orphanage. 0 modules added`） |
| エディタ | `io` / `kotlinx` / `java.io.Serializable` を含む**全シンボルが未解決** |
| Gradle ツールウィンドウ | 赤い項目なし |
| 通知 | なし |
| Build ツールウィンドウ | 「backend: 失敗」と、無関係な JVM 警告のみ |

**UI にはエラーが一切出ない。** IDE のログにだけ、次が同期 1 回につき数十回記録されていた。

```
WARN - #c.i.p.i.tunnels - Error sending data to client
	at com.intellij.platform.eel.provider.utils.EelPipeImpl.throwError(EelPipeImpl.kt:220)
WARN - #c.i.p.i.tunnels - Pipe was broken with message: Closed for receiving
```

一方、**CLI のビルドは完全に正常**だった。JDK 3 種（Corretto 25.0.4 / SDKMAN Amazon 25.0.4 / OpenJDK 26.0.2）すべてで `BUILD SUCCESSFUL`、テスト 10/10。

### 原因の特定が難航した理由

同時期に、**別の原因が重なっていた**。IntelliJ は Gradle 同期のたびに WSL の `/tmp` へ約 192MB の作業ディレクトリを作り、セッション中は削除しない。`/tmp` は systemd により **7.9GB の tmpfs（RAM 上）** としてマウントされていたため、約 40 回の同期で満杯になった。

```
NotEnoughSpace(where=/tmp/QA98Zp/gradle-api-9.6.0.jar.part, message=No space left)
```

`/tmp` を実ディスクに移して枯渇を解消しても症状が変わらず、**原因が 1 つだと思い込んだことで切り分けが長引いた**。

### 決め手

上のエラーメッセージにあるファイル名が答えだった。IDE のインストール先を確認すると、同梱されているのは **`gradle-api-9.6.0.jar`** である。

```
C:\Users\gensh\AppData\Local\Programs\IntelliJ IDEA\...\gradle-api-9.6.0.jar
```

プロジェクトは PR [#33](https://github.com/GenkiHashioka/kotlin-todo/pull/33) で Gradle を 9.5.1 から **9.7.0** に上げていた。IDE の Tooling API（IDE 側が Gradle と会話するためのクライアント）が 9.6.0 のまま、9.7 系のデーモンと通信していたことになる。

実測で境界を確認した。

| Gradle | CLI ビルド | IDE の同期 |
|---|---|---|
| **9.6.0** | ○ | **○** |
| 9.7.0 | ○ | × |
| 9.7.1 | ○ | × |

**Gradle 側のパッチリリースでは解決しない。** 9.7.1 の修正 6 件はいずれも無関係（`BaseExecSpec` のストリーム、テスト差分の表示形式、antlr の混入、`Transformer` 実装、`ant.taskdef` のクラスパス解決順、`Option` アノテーションの引数順）で、Tooling API や IDE 同期に関する項目は無い。

## Decision（何を決めたか）

### 1. Gradle は IDE が同梱する Tooling API と同じ系列に固定する

現時点では **9.6.0** とする。

```properties
# backend/gradle/wrapper/gradle-wrapper.properties
distributionUrl=https\://services.gradle.org/distributions/gradle-9.6.0-bin.zip
```

### 2. Gradle の更新は、IDE の更新とセットで検討する

Gradle 単体を上げない。**先に IDE が同梱する `gradle-api-*.jar` を確認**し、その系列を超えない範囲で上げる。

```powershell
Get-ChildItem "$env:LOCALAPPDATA\Programs\IntelliJ IDEA" -Recurse -Filter "gradle-api-*.jar" |
  Select-Object -ExpandProperty Name
```

### 3. 解除の条件を明記する

**IDE が同梱する `gradle-api-*.jar` が 9.7 以降になったら、この固定を解除してよい。** その時点で本 ADR を「置き換え済み」に更新する。

判断の根拠が **IDE のバージョンという外部条件**に紐づくため、条件が変われば結論も変わる。恒久的な決定ではない。

### 4. 症状の見分け方を残す

同じことが再発したとき、次の 3 点が揃っていれば本 ADR の問題である。

- CLI の `./gradlew build` は通る
- IDE の同期は「完了」と記録されるのにモジュールが 0 件
- IDE のログに `EelPipeImpl` の `Pipe was broken`

**UI にエラーが出ないため、ログを見ないと判別できない。**

## Consequences（この決定によって何が得られ、何を犠牲にしたか）

### 得られるもの

- **IDE が使える**。エディタの補完・型チェック・ナビゲーションが機能する。Kotlin を学ぶことが目的のプロジェクトで、これが失われる損失は大きい
- **原因不明の「失敗」に悩まされない**。症状が UI に出ないため、知らずに踏むと数時間を失う
- **CLI と IDE でビルド結果が一致する**

### 犠牲にするもの

- **最新の Gradle を使えない**。9.7 系の新機能（Isolated Projects の incubating 昇格、Configuration Cache の対応範囲拡大、Resilient Sync）が使えない。ただし**いずれも現時点で使っていない**
- **更新の手間が増える**。Gradle を上げるたびに IDE 側を確認する一手間が要る
- **IDE の更新を待つ受け身の姿勢になる**。Gradle のリリースから IDE が追従するまでのタイムラグを、そのまま受け入れることになる

### 代替案として検討したもの

- **Gradle 9.7.1 に上げる**：「WSL2 での同期問題が 9.7.1 で修正されている」という情報を確認したが、**リリースノートに該当する修正は無く、実際に試して再現した**。却下
- **IntelliJ のバージョンを上げる / 下げる**：2026.2.1 が最新の正式版で、上げる先が無い（preview 版は除く）。下げる場合、同梱される Tooling API はさらに古くなるため状況は改善しない。却下
- **リモート開発（WSL 内でバックエンドを動かす）に切り替える**：`gateway.wsl.open.projects.natively` を `false` にしてバックエンドを起動したが、**同期は同じように失敗した**。却下
- **IDE の設定とキャッシュを完全にリセットする**：`%APPDATA%` と `%LOCALAPPDATA%` の設定ディレクトリを退避して初回起動状態にしたが、**同じ症状が再現した**。却下
- **IDE を諦めて CLI だけで開発する**：ビルドもテストも CLI で完結するため、技術的には成立する。しかし補完と型チェックを失うことは、Kotlin の学習という本プロジェクトの目的に直接反する。却下
- **リポジトリを Windows 側に移す**：切り分けの途中では有力に見えた。同一 IDE で Windows 上の別プロジェクトは正常に同期できていたためである。**しかしその別プロジェクトが使っていた Gradle は 9.6.0 だった。** 制約は IDE が同梱する Tooling API のバージョンであって、プロジェクトの置き場所ではない。**Windows に移しても 9.7 系は同じように失敗する。** 本件の解決策にはならない。却下

## 関連

- [ADR 0018 - Testcontainers が使う Docker API バージョンを 1.44 に固定する](0018-pin-docker-api-version-for-testcontainers.md) — 同じく「外部ツールのバージョン差に合わせて固定する」判断
- [ADR 0013 - Gradle スクリプトを Groovy DSL から Kotlin DSL に切り替える](0013-kotlin-dsl-gradle.md)
- PR [#33](https://github.com/GenkiHashioka/kotlin-todo/pull/33) — Gradle を 9.5.1 から 9.7.0 に上げた PR
- `docs/design-notes/phase-04.10-openapi-and-swagger-ui.md` — 本件が発覚した作業
