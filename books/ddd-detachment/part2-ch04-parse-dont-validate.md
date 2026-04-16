---
title: "パースとバリデーションを同時に行う"
---

## 2ステップの問題

前章のBean Validationによる実装を振り返ると、処理は必ず2ステップを踏んでいた。

1. **バリデーション**: `OrderPlanForm` の内容が正しいか確認する
2. **変換**: 正しければ `OrderPlan`（sealed interface）に詰め替える

このとき「バリデーション通過後の `OrderPlanForm`」という中間状態が生まれる。この中間状態は「もう不正な値は入っていないはず」だが、それを型が保証しているわけではない。コンパイラには `OrderPlanForm` にしか見えないので、再度 `switch` を書かなければならない。

**"Parse, Don't Validate"** という原則がある。Alexis King が2019年に書いたブログ記事が出典だ。要点はシンプルだ。

> バリデーションは「入力が正しいかどうか」を確認するが、その結果は `boolean` にすぎない。パースは「入力が正しければ目的の型の値を返し、正しくなければエラーを返す」。パースを使えば、検証済みであることが型に刻まれる。

Raohのデコーダはこの原則の実装だ。

## デコーダとは何か

Raohでは `Decoder<I, O>` という型でパースを表現する。`I` が入力の型、`O` が出力の型だ。

```java
// JsonNode を受け取って OrderPlan を返すデコーダ
Decoder<JsonNode, OrderPlan> ORDER_PLAN_DECODER = ...;
```

デコーダを実行すると `Result<O>` が返る。`Result` は成功（`Ok`）か失敗（`Err`）のどちらかだ。

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

`Ok` の中に入っている値は `OrderPlan` だ。`OrderPlanForm` ではない。バリデーションと変換が同時に完了しているので、2回目の `switch` は不要になる。

## デコーダの組み立て

Raohではデコーダを小さな部品から組み合わせて作る。

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

`field("name", ...)` でJSONのキー名を指定する。第2引数がそのフィールドの値に適用されるデコーダだ。

### combineで複数フィールドをまとめる

```java
// StandardPlan のデコーダ
Decoder<JsonNode, OrderPlan.StandardPlan> STANDARD_PLAN_DECODER = combine(
        field("mealSetId", string().minLength(1)).map(MealSetId::new),
        field("frequency", enumOf(DeliveryFrequency.class))
).map(OrderPlan.StandardPlan::new);
```

`combine` は複数のフィールドを同時に取り出す。すべて成功すれば `.map()` に渡してオブジェクトを構築する。どれか1つでも失敗すれば、すべてのエラーをまとめて `Err` で返す。

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

`discriminate` は特定のフィールドの値を見て、使うデコーダを切り替える。

```java
public static final Decoder<JsonNode, OrderPlan> ORDER_PLAN_DECODER =
        discriminate("planType", Map.of(
                "STANDARD",  STANDARD_PLAN_DECODER,
                "PREMIUM",   PREMIUM_PLAN_DECODER,
                "CUSTOM",    CUSTOM_PLAN_DECODER
        ));
```

`"planType"` フィールドが `"STANDARD"` なら `STANDARD_PLAN_DECODER` を、`"PREMIUM"` なら `PREMIUM_PLAN_DECODER` を使う。それ以外の値が来たらエラーになる。

**`discriminate` の構造が `sealed interface` の階層と1対1で対応している**ことに注目してほしい。`OrderPlan` が `StandardPlan | PremiumPlan | CustomPlan` であるという型の定義と、`discriminate` が `"STANDARD" | "PREMIUM" | "CUSTOM"` で分岐するという構造が鏡のように対応している。

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

前章のコントローラーと比べてみよう。

- `@Validated` と `BindingResult` が消えた
- `OrderPlanForm` が消えた
- ドメインオブジェクトを組み立てるための2回目の `switch` が消えた
- `Ok` の中には最初から `OrderPlan`（具体的には `StandardPlan`、`PremiumPlan`、`CustomPlan` のどれか）が入っている

バリデーションと変換が `ORDER_PLAN_DECODER.decode(body)` の1行にまとまり、その結果を `switch` で受け取るだけだ。

## エラーパスが自動で組み立てられる

Raohはネストしたデコーダのエラーパスを自動で構成する。たとえばCUSTOMプランで `meals` が空だった場合、エラーは次のようになる。

```json
{
  "errors": [
    { "path": "/meals", "message": "1つ以上の食材を選択してください" }
  ]
}
```

`field("meals", ...)` と書いたので、エラーパスに `meals` が自動で含まれる。Bean Validationの `addPropertyNode()` を手動で書く必要はない。

---

次章では、Bean ValidationとRaohを同じドメインで並べて比較し、どちらをいつ選ぶべきかを整理する。
