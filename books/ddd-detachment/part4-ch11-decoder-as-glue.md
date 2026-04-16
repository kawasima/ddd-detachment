---
title: "デコーダがレイヤーをつなぐ"
---

## 注文フロー全体を通して見る

Part 2・3 で個別に見てきたデコーダと状態遷移を、今度はレイヤーを縦断して追う。HTTP リクエストが届いてから DB に書き込まれるまで、データがどのように変換され、どこで変換が起きるかを一覧する。

```text
HTTP Request (JsonNode)
        │
        ▼ OrderPlanDecoder（1回目の変換）
        │
  OrderPlan (sealed interface)   ← ここから先は「Always-Valid Layer」
        │
        ▼ OrderController が Subscription を組み立てる（変換なし）
        │
  Subscription.Active
        │
        ▼ InMemorySubscriptionRepository.save()（2回目の変換）
        │
  永続化ストレージ（DB）
```

変換は2回だけだ。Full Mapping で現れる `CreateOrderCommand` や `OrderData` のような中間 DTO は存在しない。

## 入口：@RequestBody JsonNode

Controller は `@RequestBody JsonNode` で受け取る。

```java
@PostMapping
public ResponseEntity<?> createOrder(@RequestBody JsonNode body) {
    Result<OrderPlan> result = OrderPlanDecoder.ORDER_PLAN_DECODER.decode(body);

    return switch (result) {
        case Ok<OrderPlan> ok -> {
            OrderPlan plan = ok.value();
            Subscription.Active subscription = new Subscription.Active(
                    new SubscriptionId(UUID.randomUUID().toString()),
                    extractUserId(body),
                    plan,
                    plan.frequency(),
                    LocalDate.now().plusWeeks(1)
            );
            subscriptionRepository.save(subscription);
            yield ResponseEntity.status(201).build();
        }
        case Err<OrderPlan> err ->
                ResponseEntity.badRequest().body(err.issues());
    };
}
```

デコーダが `Result<OrderPlan>` を返す。`Ok` なら型が確定した `OrderPlan` が手に入る。`Err` なら構造化されたエラー情報が手に入る。中間の `OrderForm` も `CreateOrderCommand` も存在しない。

`@Valid` アノテーションも `BindingResult` も不要だ。デコーダ自体がバリデーションと型変換を同時に行う。

## 型の確定とパターンマッチ

`Ok` ブランチで得られる `plan` は `OrderPlan` 型だが、`switch` を使えばプランの種類ごとに型が確定する。

```java
String description = switch (plan) {
    case OrderPlan.StandardPlan p ->
            "スタンダードプラン: " + p.mealSetId().value();
    case OrderPlan.PremiumPlan p ->
            "プレミアムプラン: " + p.mealSetId().value()
            + (p.includeFrozen() ? "（冷凍含む）" : "");
    case OrderPlan.CustomPlan p ->
            "カスタムプラン: " + p.meals().size() + "品";
};
```

コンパイラが網羅性を保証する。新しいプランの種類が追加されたとき、このコードはコンパイルエラーになる。実行時エラーとして現れる前に気付ける。

## 出口：リポジトリが詰め替えを担う

`subscriptionRepository.save(subscription)` の先は、リポジトリ実装が担う。Controller は詰め替えを知らない。

```java
// リポジトリ実装内の詰め替えロジック
@Override
public void save(Subscription subscription) {
    SubscriptionRow row = switch (subscription) {
        case Subscription.Active a -> toActiveRow(a);
        case Subscription.Suspended s -> toSuspendedRow(s);
    };
    store.put(row.id(), row);  // 実際の実装では jOOQ の DSL を使う
}

private SubscriptionRow toActiveRow(Subscription.Active a) {
    return buildRow(a.id().value(), a.userId().value(), "ACTIVE",
            a.plan(), a.frequency(), a.nextDeliveryDate());
}
```

実際のアプリケーションでは `store.put(...)` の部分が jOOQ の DSL になる。

```java
// jOOQ を使った場合の例
case Subscription.Active a -> jooq.insertInto(SUBSCRIPTIONS)
        .set(SUBSCRIPTIONS.ID, a.id().value())
        .set(SUBSCRIPTIONS.USER_ID, a.userId().value())
        .set(SUBSCRIPTIONS.STATUS, "ACTIVE")
        .set(SUBSCRIPTIONS.NEXT_DELIVERY_DATE, a.nextDeliveryDate())
        .execute();
```

ドメインモデルの `Subscription` は `@Entity` アノテーションを持たない。jOOQ は `ResultSet` を直接 Java オブジェクトにマッピングするので、ORM のようにドメインモデル自体に永続化の知識を持たせる必要がない。

## Part 1 の構成との対比

Ch 1 で示した Full Mapping の構成と比較する。

```text
【Full Mapping】
HTTP Request
    ↓
OrderForm（プレゼンテーション層）
    ↓ 詰め替え
CreateOrderCommand（ユースケース入力）
    ↓ 詰め替え
Order（ドメインモデル）
    ↓ 詰め替え
OrderEntity（データアクセス層）
    ↓
DB

4 種類のオブジェクト、3 回の詰め替え
```

```text
【本書の構成】
HTTP Request (JsonNode)
    ↓ OrderPlanDecoder（デコード）
OrderPlan（ドメインモデル）
    ↓ リポジトリ実装（永続化マッピング）
DB

2 種類のオブジェクト、2 回の変換
```

消えたものは何か。

- `OrderForm`: Bean Validation のためのフラットなクラス。デコーダが `JsonNode` を直接 `OrderPlan` に変換するので不要。
- `CreateOrderCommand`: UseCase の境界を明示するための DTO。Controller と UseCase が同じチームの同じリポジトリにあるなら不要（距離が近い）。
- `OrderEntity`: JPA のエンティティクラス。jOOQ はドメインモデルから直接 DSL で SQL を発行できるので不要。

削除したのではなく、それらの役割が別の場所に吸収された。

- `OrderForm` の役割 → `OrderPlanDecoder`（デコーダが型変換とバリデーションを担う）
- `CreateOrderCommand` の役割 → `OrderPlan` が直接 UseCase に渡る（中間 DTO なし）
- `OrderEntity` の役割 → `SubscriptionRepositoryImpl` 内の `switch` 式（リポジトリが永続化マッピングを担う）

---

Part 4 のまとめ: 詰め替えは「なくす」のではなく「適切な場所に集める」ものだ。境界（入口）ではデコーダが一回変換し、型を確定させる。内部（ドメイン層）ではドメインモデルをそのまま扱う。出口（リポジトリ実装）ではドメインモデルを永続化形式に変換する。この配置が、変更の波及を最小にしながら、詰め替えの総量も最小にする。

次の Part 5 では、こうした設計を既存のコードベースにどう導入するか、また設計判断の基準をどう立てるかを見ていく。
