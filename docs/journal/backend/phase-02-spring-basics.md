# Phase 2 — Spring Boot基礎の再確認

**ステータス**: 完了
**開始日**: 2026-07-30
**完了日**: 2026-07-31

## 学習目標

- DIコンテナの仕組み（Beanとは何か）
- Kotlinでのコンストラクタインジェクション
- レイヤードアーキテクチャ（Controller → Service → Repository）の意味
- Spring MVCのリクエストライフサイクル
- `@SpringBootApplication`が実際に行っていること

## 成果物

使い捨ての`GreetingService`（`@Service`）+ `HelloController`（`@RestController`、コンストラクタインジェクション）。`GET /hello`で `{"message":"Hello, World!"}` を返す。

```kotlin
@Service
class GreetingService {
    fun greet(name: String) = "Hello, $name!"
}

@RestController
class HelloController(private val greetingService: GreetingService) {
    @GetMapping("/hello")
    fun hello(@RequestParam name: String = "World") = mapOf("message" to greetingService.greet(name))
}
```

## チェックポイント結果

`curl "localhost:8080/hello"` → `{"message":"Hello, World!"}`（動作確認済み）

## 学んだこと

- DIコンテナ（`ApplicationContext`）がBeanのインスタンス化・依存解決を肩代わりする仕組み（IoC）
- コンストラクタインジェクションを使うと`val`のまま非null・不変で依存を保持でき、テストもしやすい
- レイヤードアーキテクチャの各層の責務分離の意図
- `@SpringBootApplication` = `@SpringBootConfiguration` + `@EnableAutoConfiguration` + `@ComponentScan`の合成であること
- `@RequestParam`のデフォルト値がSpring側で機能するのは、`kotlin-reflect`依存がクラスパスにあり、Springがリフレクションでkotlinのデフォルト引数を検知しているため

## フィールドインジェクションとの比較（演習で実際に書いて比較した）

一時的に以下のフィールドインジェクション版も書いて動作・型の違いを確認した（最終的にはコンストラクタインジェクション版のみをソースに残し、こちらはジャーナルに記録として残す）。

```kotlin
@RestController
class HelloController {
    @Autowired
    lateinit var greetingService: GreetingService // valにできない。再代入もできてしまう

    @GetMapping("/hello")
    fun hello(@RequestParam name: String = "World") = mapOf("message" to greetingService.greet(name))
}
```

比較して分かったこと:
- フィールドインジェクションは`var`必須（`val`は宣言時初期化必須のため使えない）→ 生成後に別インスタンスへ再代入できてしまう可能性が理論上残る
- `lateinit`はコンパイル時は非null型として扱われるが、実行時にSpringがセットするまでは未初期化状態。アクセスすると`UninitializedPropertyAccessException`（NPEとは別の専用例外）が発生する
- `lateinit`は`val`・プリミティブ型（Int/Boolean等）・nullable型には使えない
- `::プロパティ名.isInitialized` で初期化済みか確認できる
- コンストラクタインジェクションなら「祈らずに済む」（インスタンスが存在する時点で必ず値がある型安全性が保たれる）

## セルフチェック

- [x] field injectionにした場合の問題点を説明できる（上記参照）

## 関連する設計判断（ADR）

まだなし（Phase 3以降、実際のコード判断が発生してから `docs/decisions/` に追加）
