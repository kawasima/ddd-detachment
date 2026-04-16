---
title: "既存コードへの導入"
---

## 全面書き換えは必要ない

Part 2〜4 で示した設計は、新規プロジェクトで最初から採用するのが最もきれいだ。しかし現実には、すでに動いているコードベースに対してこのアプローチを導入したいケースの方が多い。

幸い、Always-Valid Layer とデコーダ合成は局所的に導入できる。全面書き換えなしに、影響を受けやすい箇所から段階的に改善できる。

## ステップ 1: Controller の直後にデコーダを挟む

最初に効果が出やすいのは、Controller 層だ。今まで `@Valid` と `BindingResult` で処理していたバリデーションを、Raoh デコーダに置き換える。

**Before**（Bean Validation + Form クラス）:

```java
@PostMapping("/orders")
public ResponseEntity<?> createOrder(
        @Valid @RequestBody OrderPlanForm form,
        BindingResult bindingResult) {
    if (bindingResult.hasErrors()) {
        return ResponseEntity.badRequest().body(bindingResult.getAllErrors());
    }
    // form から OrderPlan への詰め替え
    OrderPlan plan = convertToPlan(form);
    orderService.createOrder(plan);
    return ResponseEntity.status(201).build();
}
```

**After**（Raoh デコーダ）:

```java
@PostMapping("/orders")
public ResponseEntity<?> createOrder(@RequestBody JsonNode body) {
    Result<OrderPlan> result = OrderPlanDecoder.ORDER_PLAN_DECODER.decode(body);

    return switch (result) {
        case Ok<OrderPlan> ok -> {
            orderService.createOrder(ok.value());
            yield ResponseEntity.status(201).build();
        }
        case Err<OrderPlan> err ->
                ResponseEntity.badRequest().body(err.issues());
    };
}
```

`OrderPlanForm` クラス、`@Valid` アノテーション、`convertToPlan()` メソッド——これらがまとめて不要になる。

この時点では、`orderService.createOrder(OrderPlan)` の先（UseCase・Domain・Repository）は変えていない。Controller だけを変更し、テストが通ることを確認してから次に進む。

## ステップ 2: CreateOrderCommand を消す

デコーダが直接 `OrderPlan` を返すようになると、`CreateOrderCommand` のような入力 DTO の存在が薄れる。

```java
// Before: Service が Command を受け取っていた
public void createOrder(CreateOrderCommand command) {
    OrderPlan plan = toPlan(command); // 詰め替え
    // ...
}

// After: Service が OrderPlan を直接受け取る
public void createOrder(OrderPlan plan) {
    // 詰め替え不要
    // ...
}
```

`CreateOrderCommand` は同一チームが管理しているはずだ（距離が近い）。`OrderPlan` を直接受け渡せるなら、中間 DTO は不要だ。

ただし、`CreateOrderCommand` が複数のエンドポイントや外部システムから参照されている場合は慎重に判断する。外部 API のスキーマとして公開されているなら、それは「契約」であり簡単には消せない。

## ステップ 3: Entity クラスを整理する

JPA の `@Entity` アノテーションを持つドメインモデルは、永続化の知識がドメインモデルに混入している状態だ。これを jOOQ または Spring JdbcTemplate ベースのリポジトリに置き換える。

**Before**（JPA Entity がドメインモデルを兼ねている）:

```java
@Entity
@Table(name = "subscriptions")
public class Subscription {
    @Id
    private String id;
    @Enumerated(EnumType.STRING)
    private SubscriptionStatus status;
    // ...
}
```

**After**（ドメインモデルと永続化を分離）:

```java
// ドメインモデル（@Entity なし）
public sealed interface Subscription permits Subscription.Active, Subscription.Suspended {
    // ...
}

// リポジトリ実装（永続化の知識はここに集約）
public class SubscriptionRepositoryImpl implements SubscriptionRepository {
    public void save(Subscription subscription) {
        switch (subscription) {
            case Subscription.Active a -> jdbcTemplate.update(
                    "INSERT INTO subscriptions (id, status, next_delivery_date) VALUES (?, ?, ?)",
                    a.id().value(), "ACTIVE", a.nextDeliveryDate());
            case Subscription.Suspended s -> jdbcTemplate.update(
                    "INSERT INTO subscriptions (id, status) VALUES (?, ?)",
                    s.id().value(), "SUSPENDED");
        }
    }
}
```

このステップは影響範囲が大きい。最初から全モデルに適用しようとせず、変更頻度が高いエンティティや、テストが書きにくくなっているエンティティから始める。

## どこから始めるか

導入の効果が出やすい順に並べる。

| 優先度 | 作業 | 効果 |
| --- | --- | --- |
| 高 | Controller に Raoh デコーダを挟む | Form クラスと ConstraintValidator を削除できる。テストが書きやすくなる |
| 中 | UseCase 境界の Command DTO を削除 | 中間クラスの削除。Controller → UseCase の詰め替えが不要になる |
| 低 | JPA Entity をドメインモデルから分離 | ドメインモデルの純粋さが増す。影響範囲が大きいので最後に |

どのステップも「全体を一度に変える」必要はない。一つの Controller から始め、テストが通ることを確認してから次に進む。Always-Valid Layer は、一つの境界から少しずつ広げていける設計だ。

---

次章では、設計判断をどう下すかを整理する。「Raoh と Bean Validation のどちらを選ぶか」「モデル結合と契約結合のどちらを選ぶか」の判断基準をまとめる。
