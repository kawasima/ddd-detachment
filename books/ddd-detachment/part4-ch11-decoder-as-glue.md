---
title: "デコーダがレイヤーをつなぐ"
---

## 注文フロー全体を通して見る

Part 2・3 で個別に見てきたデコーダと状態遷移を、今度はレイヤーを縦断して追います。HTTP リクエストが届いてから DB に書き込まれるまで、データがどのように変換され、どこで変換が起きるかを一覧します。

```text
HTTP Request (JsonNode)
        │
        ▼ OrderPlanDecoder（1回目の変換：外部形式 → ドメイン型）
        │
  OrderPlan (sealed interface)   ← ここから先は「Always-Valid Layer」
        │
        ▼ OrderController が Subscription を組み立てる
        │   ※ OrderPlan の値はそのまま使う。新たに生成する値（ID・次回配送日）はここで作る
  Subscription.Active
        │
        ▼ SubscriptionRepository.save()（2回目の変換：ドメイン型 → 永続化形式）
        │
  永続化ストレージ（DB）
```

「変換」とここで指しているのは、**あるドメイン型を別のドメイン型や永続化形式に写し替える操作**です。Controller で `UUID.randomUUID()` や `LocalDate.now().plusWeeks(1)` を生成するのは「値の生成」であり、変換ではありません。`OrderPlan` から `Subscription.Active` を作るときも、`plan` をそのままフィールドに渡しているだけで、型を変換する詰め替えは発生していません。Full Mapping で現れる `CreateOrderCommand` や `OrderData` のような中間 DTO は存在しません。

## 入口：@RequestBody JsonNode

Controller は `@RequestBody JsonNode` で受け取ります。

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

デコーダが `Result<OrderPlan>` を返します。`Ok` なら型が確定した `OrderPlan` が手に入ります。`Err` なら構造化されたエラー情報が手に入ります。中間の `OrderForm` も `CreateOrderCommand` も存在しません。

`@Valid` アノテーションも `BindingResult` も不要です。デコーダ自体がバリデーションと型変換を同時に行います。

## 型の確定とパターンマッチ

`Ok` ブランチで得られる `plan` は `OrderPlan` 型ですが、`switch` を使えばプランの種類ごとに型が確定します。

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

コンパイラが網羅性を保証します。新しいプランの種類が追加されたとき、このコードはコンパイルエラーになります。実行時エラーとして現れる前に気付けます。

## 出口：リポジトリが詰め替えを担う

`subscriptionRepository.save(subscription)` の先は、リポジトリ実装が担います。Controller は詰め替えを知りません。

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

実際のアプリケーションでは `store.put(...)` の部分が jOOQ の DSL になります。

```java
// jOOQ を使った場合の例
case Subscription.Active a -> jooq.insertInto(SUBSCRIPTIONS)
        .set(SUBSCRIPTIONS.ID, a.id().value())
        .set(SUBSCRIPTIONS.USER_ID, a.userId().value())
        .set(SUBSCRIPTIONS.STATUS, "ACTIVE")
        .set(SUBSCRIPTIONS.NEXT_DELIVERY_DATE, a.nextDeliveryDate())
        .execute();
```

ドメインモデルの `Subscription` は `@Entity` アノテーションを持ちません。jOOQ は `ResultSet` を直接 Java オブジェクトにマッピングするので、ORM のようにドメインモデル自体に永続化の知識を持たせる必要がありません。

## Part 1 の構成との対比

Ch 1 で示した Full Mapping の構成と比較します。

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

消えたものは何でしょうか。

- `OrderForm`: Bean Validation のためのフラットなクラスです。デコーダが `JsonNode` を直接 `OrderPlan` に変換するので不要です。
- `CreateOrderCommand`: UseCase の境界を明示するための Local DTO です。デコーダがあれば不要になります（後述）。
- `OrderEntity`: JPA のエンティティクラスです。jOOQ はドメインモデルから直接 DSL で SQL を発行できるので不要です。

削除したのではなく、それらの役割が別の場所に吸収されました。

- `OrderForm` の役割 → `OrderPlanDecoder`（デコーダが型変換とバリデーションを担う）
- `CreateOrderCommand` の役割 → `OrderPlan` が直接 UseCase に渡る（中間 DTO なし）
- `OrderEntity` の役割 → `SubscriptionRepositoryImpl` 内の `switch` 式（リポジトリが永続化マッピングを担う）

### なぜ `CreateOrderCommand` が不要になるのか

`CreateOrderCommand` のような Local DTO（UseCase 専用の入力クラス）が生まれる根本的な理由は、**Bean Validation のバリデーション結果が型に反映されない**ことにあります。

Bean Validation を通過した後のオブジェクトは `OrderPlanForm` のままです。`planType` は `String`、`mealSetId` も `String` のまま——ドメインの型（`OrderPlan`）ではありません。Controller がこの `OrderPlanForm` を UseCase に直接渡してしまうと、UseCase がプレゼンテーション層のクラス（`OrderPlanForm`）に依存することになります。それを避けるために `CreateOrderCommand` という中間の Local DTO が登場します。

```text
OrderPlanForm  →（詰め替え）→  CreateOrderCommand  →（詰め替え）→  OrderPlan
   Controller                    Controller/UseCase境界               UseCase内
```

Raoh のデコーダは `JsonNode` を受け取り、直接 `OrderPlan` を返します。`OrderPlanForm` という中間状態が存在しないので、そもそも「プレゼンテーション層の型を UseCase に渡してしまう」という問題が起きません。デコードの結果はすでにドメイン型なので、`CreateOrderCommand` を経由する理由がなくなります。

```text
JsonNode  →（デコード）→  OrderPlan
                           そのまま UseCase へ
```

Local DTO がなくなることで、フィールドを1つ追加したときの修正箇所が減ります。Ch 2 で示した「フィールド追加が複数クラスに波及する」問題の一因がここにあります。

---

Part 4 のまとめ: 詰め替えは「なくす」のではなく「適切な場所に集める」ものです。境界（入口）ではデコーダが一回変換し、型を確定させます。内部（ドメイン層）ではドメインモデルをそのまま扱います。出口（リポジトリ実装）ではドメインモデルを永続化形式に変換します。この配置が、変更の波及を最小にしながら、詰め替えの総量も最小にします。

次の Part 5 では、こうした設計を既存のコードベースにどう導入するか、また設計判断の基準をどう立てるかを見ていきます。
