# 0018 - Testcontainers が使う Docker API バージョンを 1.44 に固定する

**ステータス**: 採用
**日付**: 2026-08-23

## Context（背景・何を解決したいか）

[ADR 0012](0012-testcontainers-for-integration-test.md) で統合テストを Testcontainers 経由の実 PostgreSQL に対して走らせると決めた。Phase 4.6 以降、この方式でテストは正常に動いていた。

Phase 4.9 の途中で、開発機の Docker Engine が 29 系に上がったのを境に**全テストが実行不能**になった（[#25](https://github.com/GenkiHashioka/kotlin-todo/issues/25)）。

```
java.lang.IllegalStateException: Could not find a valid Docker environment.
	at org.testcontainers.dockerclient.DockerClientProviderStrategy.getFirstValidStrategy

UnixSocketClientProviderStrategy: failed with exception BadRequestException (Status 400: ...)
```

この状態のまま Phase 4.9 (b) / (c) を進めたため、**Routing・DTO・バリデーション・エラー応答のすべてが自動テスト無しで実装された**。品質は `docs/journal/phase-04.9c-konform-and-status-pages.md` に記録した手動 curl の結果だけに支えられている。Phase 4.10 以降も同じ状態を続けると未検証の実装が積み上がるため、ここで解消することにした。

### 原因

Docker Engine 29 から、**API バージョン 1.40 未満のクライアントが拒否される**ようになった。実測すると挙動が分かれる。

| リクエスト | 結果 |
|---|---|
| `/v1.32/_ping` | **400** |
| `/v1.40/_ping` | 200 |
| `/v1.44/_ping` | 200 |
| `/_ping`（バージョン無指定） | 200 |

一方、Testcontainers の `DockerClientProviderStrategy` はこう実装されている（バイトコードで確認）。

```java
if (config.getApiVersion() == RemoteApiVersion.UNKNOWN_VERSION) {
    builder.withApiVersion(RemoteApiVersion.VERSION_1_32);
}
```

**API バージョンが未設定なら 1.32 を使う**という既定値が入っている。Docker 側の下限が 1.40 に上がった結果、この既定値がそのまま拒否対象になった。

Testcontainers 側のバグではあるが、**1.21.3（2026-08 時点の最新）のバイトコードも同一で、上げても直らない**ことを確認済み。

## Decision（何を決めたか）

### 1. テスト JVM のシステムプロパティで `api.version` を明示する

`backend/build.gradle.kts`:

```kotlin
tasks.test {
	useJUnitPlatform()
	// Docker Engine 29 は APIバージョン 1.40未満のクライアントを拒否するようになった。
	// Testcontainers は api.version が未設定だと現状 1.32 を既定値として使用するため明示的にバージョンを指定する。(#25)
	systemProperty("api.version", "1.44")
}
```

上の `if` の裏返しである。**値が入っていれば `UNKNOWN_VERSION` ではなくなり、1.32 へのフォールバックに入らない。**

### 2. 経路はシステムプロパティのみが有効

API バージョンを外から与える経路は 4 つ考えられるが、**実際に効くのは 1 つだけ**だった。

| 経路 | 効くか | 理由 |
|---|---|---|
| 環境変数 `DOCKER_API_VERSION` | **効かない** | docker-java が環境変数から読むのは `DOCKER_HOST` / `DOCKER_CONTEXT` / `DOCKER_TLS_VERIFY` / `DOCKER_CONFIG` / `DOCKER_CERT_PATH` の 5 つのみ |
| `~/.testcontainers.properties` | **効かない** | Testcontainers 側にそのキーが存在しない |
| `~/.docker-java.properties` | 効く | ただし開発機ごとの設定でリポジトリに残らない |
| **JVM システムプロパティ `api.version`** | **効く** | `DefaultDockerClientConfig` の定数 `API_VERSION = "api.version"`。リポジトリに残せる |

Testcontainers はテストと同じ JVM 内で Docker クライアントを組み立てるため、Gradle の `systemProperty` がそのまま届く。

### 3. 値は 1.44 とする

**サーバが受け付ける範囲のうち、クライアントが理解できる最大値**を選ぶ。

| | 値 | 意味 |
|---|---|---|
| Docker Engine | 29.7.2 | デーモン本体 |
| `MinAPIVersion` | 1.40 | これ未満は拒否 |
| `ApiVersion` | 1.55 | デーモンが喋れる最大 |
| docker-java 3.4.0 の上限 | 1.44 | `RemoteApiVersion` の定数がここまで |
| **採用値** | **1.44** | 上 2 つの重なりの最大 |

**下限の 1.40 にしない**: 1.41〜1.44 で入った API を使えなくなる。今のところ必要ないが、あえて狭める理由もない。

**1.55 にしない**: docker-java 3.4.0 にその契約を理解するコードが無い。無視して動く可能性は高いが、検証されていない領域に入る。

### 4. Testcontainers 1.20.4 のまま据え置く

1.21.3 でも同じ既定値なので、**上げても解決しない**。この問題を理由にした更新はしない。

## Consequences（この決定によって何が得られ、何を犠牲にしたか）

### 得られるもの

- **テストが動く**: Repository 5 件 + Service 4 件、計 9 件が green に戻った
- **Phase 4.11 の前提が整った**: `testApplication` による Routing テストを書ける状態になった。journal に手動 curl で記録した確認項目をテストコードに移植できる
- **設定がリポジトリに残る**: 開発機ごとの `~/.docker-java.properties` ではなく `build.gradle.kts` に書いたので、別のマシンや CI でも同じ挙動になる
- **ライブラリを上げずに済む**: Testcontainers 1.20.4 のまま。[ADR 0012](0012-testcontainers-for-integration-test.md) が記録した依存構成が変わらない

### 犠牲にするもの

- **バージョン番号がソースにハードコードされる**: `1.44` という数字が `build.gradle.kts` に直接書かれる。Docker と docker-java 両方の都合で決まった値なので、どちらかが動けば見直しが要る
- **将来また壊れうる**: Docker が `MinAPIVersion` を 1.44 より上げると同じ症状が再発する。そのときは docker-java（＝ Testcontainers）側が新しいバージョンに対応するまで、この手では逃げられない
- **上流のバグを迂回しているだけ**: Testcontainers が既定値を直せばこの設定は不要になる。将来この 3 行を消せるかどうかは、上流を見に行かないと分からない

### 代替案として検討したもの

- **Testcontainers を 1.21.3 に上げる**: 最初に試す価値のある手だが、**1.21.3 の `DockerClientProviderStrategy` も `VERSION_1_32` のまま**であることをバイトコードで確認した。効果なし。却下
- **環境変数 `DOCKER_API_VERSION` を設定する**: Docker CLI が読む変数なので自然に見えるが、docker-java は環境変数からこのキーを読まない。実際にテストを走らせて無効であることを確認した。却下
- **API バージョンを指定せずに投げる**: `/_ping` はバージョン無指定でも 200 を返す（デーモンが自身の最新で応答する）。しかし Testcontainers が「未設定なら 1.32」と補完してしまうため、docker-java に無指定で投げさせる手段が無い。却下
- **`~/.docker-java.properties` に書く**: 設定としては効くが、開発機のホームディレクトリに置くためリポジトリに残らない。別マシンや CI で同じ問題が再発する。却下
- **Docker Engine を 28 系に戻す**: 症状は消えるが、29 が既定になる以上いずれ同じ問題に戻る。開発環境を古い状態に固定する代償の方が大きい。却下

## 関連

- [ADR 0012 - 統合テストで Testcontainers（実 PostgreSQL コンテナ）を使う](0012-testcontainers-for-integration-test.md) — 前提となる採用判断
- [#25](https://github.com/GenkiHashioka/kotlin-todo/issues/25) — 本件の Issue
- `backend/build.gradle.kts` — 設定の実物
- `backend/src/test/kotlin/com/example/kotlin_todo/AbstractPostgresTest.kt` — コンテナ起動箇所
- `docs/journal/phase-04.9c-konform-and-status-pages.md` — テストが動かない間、手動 curl で代替した記録
