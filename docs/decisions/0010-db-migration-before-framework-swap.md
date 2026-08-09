# 0010 - DB 移行（PostgreSQL + Flyway + Testcontainers 化）を Web フレームワーク移行より先に行う

**ステータス**: 採用
**日付**: 2026-08-09

## Context（背景・何を解決したいか）

ADR 0008 で Spring Boot → Ktor 移行が決まり、ADR 0009 でモノレポ構成に整理された。Phase 4.5 で移行の土台（`v0.4-spring-final` タグ、`backend/` への移設、JDK 25、docker-compose ルート配置）が整い、次に「実装をどの順序で置き換えるか」を決める段階に入った。

承認済みプラン file（`~/.claude/plans/pc-springboot-kotlin-ktor-mac-giggly-key.md`）では、以下の順序が指定されていた：

| 元の順序 | 内容 |
|---|---|
| Phase 4.6 | Ktor 骨組み + `/health`（Spring 削除、build.gradle.kts 化） |
| Phase 4.7 | Exposed + Flyway + PostgreSQL + Testcontainers |
| Phase 4.8 | Ktor Routing + DTO + Service + StatusPages + Konform（CRUD 復活） |
| Phase 4.9 | OpenAPI / Swagger UI 再構築 |
| Phase 4.10 | テスト戦略再構築 |

この順序は「Kotlin native な書き心地（Ktor / Exposed / coroutines）に早く触れる」ことを優先した学習カリキュラム的設計だった。

Phase 4.6 実装着手の段階でこの順序を再検討したところ、次の観点が浮上した：

- **変更軸の分離**: Ktor 先行順序では Phase 4.7 で「DB エンジン切替（H2 → PostgreSQL）」「schema 管理方式切替（`ddl-auto` → Flyway）」「ORM 切替（JPA → Exposed）」「テスト方式切替（`@DataJpaTest` → Testcontainers）」が同時に発生する。1 つの Phase で 4 つの変更軸が重なると、失敗時の切り分けが難しい。
- **中間状態の自然さ**: Ktor 先行順序では Phase 4.6 完了直後から Phase 4.8 完了までの期間、main には「`/health` しか返さない Ktor アプリ」が居座る。実務では見ない不自然な中間状態。DB 先行順序なら「Spring + JPA + PostgreSQL + Flyway」という実務でも普通に存在する構成を経由する。
- **ロールバック粒度**: DB 先行順序なら Phase 4.6 を revert しても Spring + JPA + H2 の完全動作状態に戻れる。Ktor 先行順序では Phase 4.6 revert = Spring 版に戻るが、Phase 4.7 revert = 「Ktor + `/health` だけ」に戻るしかない。
- **学習スタイルとの相性**: 学習者本人が「独立したフェーズでじっくり学ぶ」ことを好むと表明した。Kotlin native なスタックに触れる時期が 1 フェーズ後ろにずれても、集中して学べる方を優先したい。

## Decision（何を決めたか）

Phase 4.6〜4.11 の順序を組み直し、**DB 移行を Web フレームワーク移行より先に行う**：

| 新順序 | 内容 |
|---|---|
| **Phase 4.6** | **DB 移行（Spring + JPA のまま H2 → PostgreSQL + Flyway + Testcontainers）** |
| Phase 4.7 | Ktor 骨組み + `/health`（Spring 削除、build.gradle.kts 化） |
| Phase 4.8 | Exposed + Repository 層 |
| Phase 4.9 | DTO + Service + Ktor Routing + StatusPages + Konform + 手動 DI（CRUD API 復活） |
| Phase 4.10 | OpenAPI / Swagger UI 再構築 |
| Phase 4.11 | テスト戦略再構築（Ktor 版で書き直し） |

**Phase 4.6 の内訳**（本 ADR と同じブランチで開始する PR 分割）:

- (a) 本 ADR + プラン file の順序組み直し（コード変更なし）
- (b) `application.properties` を PostgreSQL 用に切替、Flyway 依存追加、`V1__init.sql` を `docs/db-schema.md` から起こす
- (c) 既存の JPA テストを Testcontainers 対応に切替

ADR 0008 で挙げた技術選定（Ktor / Exposed / 手動 DI / Konform / kotlinx.serialization / PostgreSQL / Flyway）そのものは変更しない。**実装する順序だけを組み替える**判断である。

## Consequences（この決定によって何が得られ、何を犠牲にしたか）

### 得られるもの

- **変更軸が Phase ごとに分離される**: 各 Phase で「何が変わったか」を単一の軸で説明できる。Phase 4.6 = DB エンジン + schema 管理 + テスト方式、Phase 4.7 = Web フレームワーク + Gradle DSL、Phase 4.8 = ORM。git log と PR 履歴が「学びの筋書き」としても綺麗になる。
- **中間状態が実務でも見る自然な構成**: Phase 4.6 完了時点は「Spring + JPA + PostgreSQL + Flyway + Testcontainers」で、CRUD API は完全動作。作品としてリポジトリを開いた時に「作りかけの塔」感が少ない。
- **ロールバック粒度が細かい**: Phase 4.6 を revert しても Spring + JPA + H2 で動作、Phase 4.7 を revert しても Spring + JPA + PostgreSQL + Flyway で動作。壊してもすぐ戻れる。
- **DB 側のバグと Web 側のバグが混ざらない**: Flyway migration の書き間違い、PostgreSQL 固有の型の扱い、`ddl-auto` からの脱却で見えてくる暗黙依存などを、Spring 側で完全に片付けてから Ktor に進める。

### 犠牲にするもの

- **Testcontainers の Repository テスト実装は書き直しになる**: Phase 4.6 で書く Testcontainers テストは Spring Data JPA の作法。Phase 4.11 で Ktor + Exposed 用に書き直しになる。再利用できるのは Testcontainers の起動設定（`PostgreSQLContainer` インスタンス、connection URL 取得ロジック）だけで、テスト本体は書き直し。学習コストとしては「JPA でも Exposed でも Testcontainers の同じ仕組みを 2 回体験する」ため無駄ではないが、生産性の観点では非効率。
- **Kotlin native な書き心地に触れるのが 1 フェーズ遅くなる**: Ktor / Exposed / coroutines に実際に手を動かすのが Phase 4.7 以降になる。学習モチベーションのピークが後ろ倒し。
- **Phase 数が 1 増える（4.10 → 4.11）**: 小粒だが管理コストがわずかに増える。プラン file の Phase 番号との対応関係も 1 ずつずれる。

### 代替案として検討したもの

- **プラン file 通りの Ktor 先行**: 元プランのまま Phase 4.6 = Ktor 骨組み、Phase 4.7 = Exposed + PostgreSQL + Flyway + Testcontainers。Testcontainers を最初から Ktor + Exposed 用に書けるので資産の使い回しが 1 度で済み、Kotlin native な学びが早い。ただし変更軸の集中と「`/health` だけの Ktor」中間状態が発生する。学習者が「独立フェーズでじっくり」を優先したため却下。
- **DB-β（Spring + Exposed の中間状態）**: Web を Spring に保ったまま JPA → Exposed だけ先に置き換える案。`@Transactional`（AOP ベースの同期モデル）と Exposed の `newSuspendedTransaction`（coroutines ベース）の思想が衝突し、Spring MVC で suspend を扱うのが不自然になる。「一時的にしか存在しない Spring + Exposed 構成」を作る価値も薄いため却下。
- **DB 先行だが Testcontainers は Ktor 移行と同時**: Phase 4.6 で PostgreSQL + Flyway 化だけ行い、Testcontainers は Phase 4.11 まで導入しない案。Phase 4.6 完了時点で既存の H2 前提テストが動かなくなるため、テストを `@Disabled` で放置する期間ができる。テストが green な状態を維持したいので却下し、Phase 4.6 に Testcontainers 化まで含めることにした。

## 関連

- [ADR 0008 - Spring Boot から Ktor へ移行する](0008-migrate-from-spring-to-ktor.md)
- [ADR 0009 - モノレポ構成（backend/ と将来の frontend/）を採用する](0009-monorepo-structure.md)
- プラン file: `~/.claude/plans/pc-springboot-kotlin-ktor-mac-giggly-key.md`（ローカル参照、リポジトリ管理外）— 本 ADR に合わせて Phase 4.6〜4.11 の順序を書き換えた
- Phase 4.6 実装過程で発生する追加 ADR（Flyway 採用理由、Testcontainers 採用理由など）は、本 ADR とは別に 0011 以降で個別に記録する
