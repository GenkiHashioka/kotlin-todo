# 0008 - Spring Boot から Ktor へ移行する

**ステータス**: 採用
**日付**: 2026-08-08

## Context（背景・何を解決したいか）

Phase 1〜4 は Spring Boot で進めた。Kotlin をホスト言語としながらも、実際に書くコードの多くは Spring のアノテーション（`@RestController`, `@Service`, `@Transactional`, `@Entity`, `@Autowired`）と JPA の作法に占められていた。これはこれで実務に近い体験だったが、**「Kotlin 言語そのものの書き方（coroutines / DSL / null 安全 / 拡張関数）を身につけたい」という当初の学習目的からは距離がある**という自覚が生じた。

また、開発環境が Mac → Windows(WSL2) に移行し、Docker Compose ベースで実運用に近い構成を組みやすくなったのを機に、技術選定を一度見直したい状況だった。

## Decision（何を決めたか）

Phase 4 の到達点（Todo CRUD、認証なし）を維持したまま、Web フレームワークを **Spring Boot から Ktor に置き換える**。同時に周辺スタックも Kotlin native な選択に寄せる：

- **Web**: Spring Boot → **Ktor (server-netty embedded)**
- **ORM**: JPA/Hibernate → **Exposed (JetBrains 公式 Kotlin DSL)**
- **DI**: Spring DI コンテナ → **手動 DI**（`main` 関数で依存グラフを組み立て）
- **バリデーション**: Bean Validation → **Konform**
- **例外→HTTP マッピング**: `@RestControllerAdvice` → **Ktor StatusPages プラグイン**
- **シリアライズ**: Jackson → **kotlinx.serialization**
- **DB**: H2 → **PostgreSQL (Docker Compose)** + Flyway
- **非同期**: 同期 → **coroutines（すべての Service / Repository を `suspend` で貫く）**

Spring 版のコードは `v0.4-spring-final` タグでアーカイブし、いつでも checkout で復元できる状態を保つ。Phase 1〜4 の journal と ADR 0001〜0007 も**そのまま保全**する（Spring 学習の記録として、また Exposed / Ktor との対比教材として価値がある）。

Ktor 版でも維持される判断:
- ADR 0001（enum を STRING で保存）
- ADR 0002（User 削除時に Todo/Category を CASCADE 削除）
- ADR 0003（Category 名はユーザーごとに一意）
- ADR 0004（Category 削除で Todo.category を NULL に）
- ADR 0005（更新は PUT 形式）
- ADR 0006（DELETE は 200 + ボディを返す）
- ADR 0007（認証実装まで固定ユーザー）

## Consequences（この決定によって何が得られ、何を犠牲にしたか）

### 得られるもの

- **Kotlin idiomatic な書き方に日常的に触れる**: ルーティングは DSL、DB アクセスは Exposed DSL、依存組み立ては素の Kotlin コード、非同期は `suspend` の伝播。フレームワークが「Kotlin を Java っぽく書けるようにする」層を挟まない分、言語の設計判断（DSL の作り方、null 安全の実践、拡張関数の使いどころ）に自分で向き合う場面が増える。Spring 版とは違う筋肉が鍛えられる。
- **実運用に近い構成に自然に寄る**: H2 in-memory を捨て PostgreSQL + Flyway を採用することで、スキーマ管理・接続プール・マイグレーションの実践経験が積める。
- **Spring で得た概念的な理解は消えない**: DI・レイヤードアーキテクチャ・`@Transactional` の意味・JPA の永続化コンテキスト・DTO 分離の考え方はフレームワーク非依存の資産として残る。むしろ Exposed の「明示的トランザクション」と対比することで JPA 側の理解も深まる。

### 犠牲にするもの

- **Spring Boot の周辺エコシステム（Security / Data JPA / Cloud / Batch など）を今のプロジェクトでは学べなくなる**。これらを深めたい場合は、別プロジェクトを新規に立てて集中的に学ぶ方針とする。
- **移行フェーズ Phase 4.5〜4.10 の間、機能追加はできない**（本来 Phase 5 に進めていた「フィルタ/ソート/検索/ページネーション」が数フェーズ後ろにずれる）。純粋な学習投資期間。
- **`springdoc-openapi` の自動生成が失われる**。Ktor 側の OpenAPI 支援は Spring 版ほど強力ではないため、部分的に手書き運用になる可能性（詳細は Phase 4.9 の ADR で決める）。
- **書き直しの手間**: `@Valid`, `@Transactional`, `@RestControllerAdvice`, `@Entity`, `@ManyToOne` などの Spring 固有アノテーションに紐付いていた実装は、対応する Ktor / Exposed / Konform / StatusPages の書き方にすべて書き直す必要がある。

### 代替案として検討したもの

- **Spring Boot のまま Phase 5 以降に進む**: 学習カリキュラム的には最速だが、「Kotlin 言語自体を深めたい」という当初の目的からズレる。却下。
- **Ktor 版を別リポジトリ / 別ディレクトリで並行構築**: Spring 版を残し比較できるが、両方を並行して育てる労力が発生し、「今どちらが最新か」も追いにくくなる。同一リポジトリで置き換え、Spring 版は tag で保全する方式を採用。
- **DI に Koin を導入**: 選択肢としてはあるが、Ktor 公式チュートリアルは手動 DI を推奨しており、学習目的では「依存グラフを自分の手で組み立てる」経験が有益なので不採用（別 ADR で扱う）。

## 関連

- プラン file: `~/.claude/plans/pc-springboot-kotlin-ktor-mac-giggly-key.md`（Claude Code の承認済みプラン。ローカル環境依存でリポジトリ管理外）— Phase 4.5〜4.10 の詳細な段取りと技術選定はここに一次ソースがある
- [ADR 0009 - モノレポ構成を採用する](0009-monorepo-structure.md)
- Phase 4.5〜4.10 の移行過程で発生する個別の技術判断は、以降の ADR に順次追加していく
