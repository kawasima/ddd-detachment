---
title: "パースとバリデーションを同時に行う"
---

## 2ステップの問題

前章のBean Validationによる実装を振り返ると、処理は必ず2ステップを踏んでいました。

1. **バリデーション**: `OrderPlanForm` の内容が正しいか確認します
2. **変換**: 正しければ `OrderPlan`（sealed interface）に詰め替えます

このとき「バリデーション通過後の `OrderPlanForm`」という中間状態が生まれます。この中間状態は「もう不正な値は入っていないはず」ですが、それを型が保証しているわけではありません。コンパイラには `OrderPlanForm` にしか見えないので、再度 `switch` を書かなければなりません。

この2ステップの構造を整理した考え方があります。Alexis King が2019年のブログ記事で書いた **"Parse, Don't Validate"** です。要点はシンプルです。

> バリデーションは「入力が正しいかどうか」を確認しますが、その結果は `boolean` にすぎません。パースは「入力が正しければ目的の型の値を返し、正しくなければエラーを返します」。パースを使えば、検証済みであることが型に刻まれます。

本書では、この考え方を Java で実践するために **Raoh**（ラオウ）というライブラリを使います。Raoh はデコーダを組み合わせて入力のパースと型変換を同時に行うライブラリです。

## デコーダとは何か

Raohでは `Decoder<I, O>` という型でパースを表現します。`I` が入力の型、`O` が出力の型です。

```java
// JsonNode を受け取って OrderPlan を返すデコーダ
Decoder<JsonNode, OrderPlan> ORDER_PLAN_DECODER = ...;
```

デコーダを実行すると `Result<O>` が返ります。`Result` は成功（`Ok`）か失敗（`Err`）のどちらかです。

```java
switch (ORDER_PLAN_DECODER.decode(jsonNode)) {
    case Ok(OrderPlan plan) -> {
        // ここでは plan が OrderPlan であることが型で保証されている
    }
    case Err(var issues) -> {
        // バリデーションエラーの詳細が issues に入っている
    }
}
```

`Ok` の中に入っている値は `OrderPlan` です。`OrderPlanForm` ではありません。バリデーションと変換が同時に完了しているので、2回目の `switch` は不要になります。

## デコーダの組み立て

Raohではデコーダを小さな部品から組み合わせて作ります。

### 基本のデコーダ

```java
// 文字列フィールドを取り出す
Decoder<JsonNode, String> titleDecoder =
        field("title", string().minLength(1).maxLength(100));

// 整数フィールドを取り出す
Decoder<JsonNode, Integer> countDecoder =
        field("count", int_().min(1).max(99));

// 日付フィールドを取り出す
Decoder<JsonNode, LocalDate> startDateDecoder =
        field("startDate", string().date());
```

`field("name", ...)` でJSONのキー名を指定します。第2引数がそのフィールドの値に適用されるデコーダです。

### combineで複数フィールドをまとめる

```java
// StandardPlan のデコーダ
Decoder<JsonNode, OrderPlan.StandardPlan> STANDARD_PLAN_DECODER = combine(
        field("mealSetId", string().minLength(1)).map(MealSetId::new),
        field("frequency", enumOf(DeliveryFrequency.class))
).map(OrderPlan.StandardPlan::new);
```

`combine` は複数のフィールドを同時に取り出します。すべて成功すれば `.map()` に渡してオブジェクトを構築します。どれか1つでも失敗すれば、すべてのエラーをまとめて `Err` で返します。

```java
// PremiumPlan のデコーダ
Decoder<JsonNode, OrderPlan.PremiumPlan> PREMIUM_PLAN_DECODER = combine(
        field("mealSetId", string().minLength(1)).map(MealSetId::new),
        field("frequency", enumOf(DeliveryFrequency.class)),
        field("includeFrozen", bool())
).map(OrderPlan.PremiumPlan::new);

// 開始日のデコーダ。業務ルール（3日以上先）を含むのでDecoderのラムダで書く
Decoder<JsonNode, LocalDate> START_DATE_DECODER = (in, path) -> {
    Result<LocalDate> result = field("startDate", string().date()).decode(in, path);
    return result.flatMap(d ->
            d.isBefore(LocalDate.now().plusDays(3))
                    ? Result.fail(path.append("startDate"), "startDate.tooSoon", "開始日は3日以上先の日付を指定してください")
                    : Result.ok(d));
};

// CustomPlan のデコーダ
Decoder<JsonNode, OrderPlan.CustomPlan> CUSTOM_PLAN_DECODER = combine(
        field("meals", list(string().minLength(1)).minSize(1))
                .map(ids -> ids.stream().map(MealId::new).toList()),
        field("frequency", enumOf(DeliveryFrequency.class)),
        START_DATE_DECODER
).map(OrderPlan.CustomPlan::new);
```

### discriminateで分岐する

`discriminate` は特定のフィールドの値を見て、使うデコーダを切り替えます。

```java
public static final Decoder<JsonNode, OrderPlan> ORDER_PLAN_DECODER =
        discriminate("planType", Map.of(
                "STANDARD",  STANDARD_PLAN_DECODER,
                "PREMIUM",   PREMIUM_PLAN_DECODER,
                "CUSTOM",    CUSTOM_PLAN_DECODER
        ));
```

`"planType"` フィールドが `"STANDARD"` なら `STANDARD_PLAN_DECODER` を、`"PREMIUM"` なら `PREMIUM_PLAN_DECODER` を使います。それ以外の値が来たらエラーになります。

**`discriminate` の構造が `sealed interface` の階層と1対1で対応していること**に注目してください。`OrderPlan` が `StandardPlan | PremiumPlan | CustomPlan` であるという型の定義と、`discriminate` が `"STANDARD" | "PREMIUM" | "CUSTOM"` で分岐するという構造が鏡のように対応しています。

## コントローラーはシンプルになる

```java
@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody JsonNode body) {
        return switch (ORDER_PLAN_DECODER.decode(body)) {
            case Ok(OrderPlan plan) -> {
                String subscriptionId = subscriptionService.create(plan);
                yield ResponseEntity.ok(Map.of("subscriptionId", subscriptionId));
            }
            case Err(var issues) -> {
                List<Map<String, String>> errors = issues.asList().stream()
                        .map(i -> Map.of(
                                "path", i.path().toString(),
                                "message", i.message()))
                        .toList();
                yield ResponseEntity.badRequest().body(Map.of("errors", errors));
            }
        };
    }
}
```

前章のコントローラーと比べてみましょう。

- `@Validated` と `BindingResult` が消えました
- `OrderPlanForm` が消えました
- ドメインオブジェクトを組み立てるための2回目の `switch` が消えました
- `Ok` の中には最初から `OrderPlan`（具体的には `StandardPlan`、`PremiumPlan`、`CustomPlan` のどれか）が入っています

バリデーションと変換が `ORDER_PLAN_DECODER.decode(body)` の1行にまとまり、その結果を `switch` で受け取るだけです。

## エラーパスが自動で組み立てられる

Raohはネストしたデコーダのエラーパスを自動で構成します。たとえばCUSTOMプランで `meals` が空だった場合、エラーは次のようになります。

```json
{
  "errors": [
    { "path": "/meals", "message": "1つ以上の食材を選択してください" }
  ]
}
```

`field("meals", ...)` と書いたので、エラーパスに `meals` が自動で含まれます。Bean Validationの `addPropertyNode()` を手動で書く必要はありません。

## I/O を伴うバリデーション

ここまでのデコーダは、JSON の値を読み取るだけの**純粋な変換**でした。しかし実際のアプリケーションでは、「指定されたミールセットが存在するかどうか」をデータベースで確認するような、**I/O を伴うバリデーション**が必要になる場面があります。

Raoh のデコーダは `flatMap` を使うことで、I/O の結果を変換パイプラインに組み込めます。

### Gateway インターフェース

まず、ドメイン層に I/O のインターフェースを定義します。

```java
// ドメイン層のインターフェース。実装の詳細（DB など）は知らない
public interface MealSetGateway {
    Result<MealSet> findById(MealSetId id);
}
```

`findById` は `Result<MealSet>` を返します。見つかれば `Ok(MealSet)`、見つからなければ `Err`（エラーメッセージ付き）です。

### デコーダに I/O を組み込む

```java
// MealSetId の文字列をデコードし、さらに DB で存在確認する
public static Decoder<JsonNode, MealSet> mealSet(MealSetGateway gateway) {
    return field("mealSetId", string().minLength(1))
            .map(MealSetId::new)
            .flatMap(gateway::findById);
}
```

`flatMap(gateway::findById)` の部分が I/O の実行点です。`field(...)` でフォーマットを確認し、`.map(MealSetId::new)` で型を確定させ、`.flatMap(gateway::findById)` で存在確認を行います。どこかで失敗すれば、それ以降の処理はスキップされ、エラーが返ります。

### コントローラーへの組み込み

```java
@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    private final Decoder<JsonNode, OrderPlan> orderPlanDecoder;

    public SubscriptionController(MealSetGateway mealSetGateway) {
        // MealSet の存在確認を含むデコーダを組み立てる
        Decoder<JsonNode, OrderPlan.StandardPlan> standardPlanDecoder = combine(
                mealSet(mealSetGateway),          // DB で存在確認
                field("frequency", enumOf(DeliveryFrequency.class))
        ).map(OrderPlan.StandardPlan::new);

        this.orderPlanDecoder = discriminate("planType", Map.of(
                "STANDARD", standardPlanDecoder,
                // ...
        ));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody JsonNode body) {
        return switch (orderPlanDecoder.decode(body)) {
            case Ok(OrderPlan plan) -> { /* ... */ }
            case Err(var issues)    -> { /* ... */ }
        };
    }
}
```

`MealSetGateway` をコンストラクタで受け取り、デコーダの組み立てに渡します。コントローラーのエンドポイントメソッド自体はシンプルなまま保たれます。

### 責務の整理

この設計では、3種類の検証が「いつ実行されるか」という順序で明確に並んでいます。

| 実行タイミング | 種類 | 記述場所 | 例 |
| --- | --- | --- | --- |
| デコード時（同期） | フォーマット検証 | `string().minLength(1)` などのデコーダ | 空文字でないこと、日付形式であること |
| デコード時（同期・DB アクセスあり） | 存在確認 | `flatMap(gateway::findById)` | ミールセットが DB に存在すること |
| ドメインオブジェクト確定後 | ビジネスルール検証 | Always-Valid Layer 内の振る舞い | カスタムプランの食材数が制限内か |

`flatMap(gateway::findById)` の呼び出しは、`decode()` が実行された時点で同期的に DB アクセスが走ります。遅延評価ではありません。フォーマット検証が通った値に対してのみ DB アクセスが走るため、不正な入力で無駄なクエリが発生しません。

フォーマット検証が通ってはじめて存在確認が実行され、存在確認が成功してはじめてドメインオブジェクトが確定します。エラーが起きた時点でパイプラインが止まるため、後続の処理はスキップされます。ビジネスルール検証（食材数の上限など）はドメインオブジェクトが確定した後、振る舞いクラスの中で行われます。これは Part 3 で詳しく見ていきます。

---

次章では、Bean ValidationとRaohを同じドメインで並べて比較し、どちらをいつ選ぶべきかを整理します。
