---
title: "ドメインモデルはただのrecordでいい"
---

## 「重いドメインモデル」の正体

DDDの文脈で「ドメインモデル」というとき、しばしば次のような実装が現れる。

```java
@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    private String id;

    @NotNull
    private String userId;

    @Enumerated(EnumType.STRING)
    @NotNull
    private SubscriptionStatus status;

    private LocalDate nextDeliveryDate;

    // JPA用のデフォルトコンストラクタ
    protected Subscription() {}

    public void suspend() {
        if (this.status == SubscriptionStatus.SUSPENDED) {
            throw new IllegalStateException("すでに一時停止中です");
        }
        this.status = SubscriptionStatus.SUSPENDED;
        this.nextDeliveryDate = null;
    }

    public void resume(LocalDate nextDeliveryDate) {
        if (this.status != SubscriptionStatus.SUSPENDED) {
            throw new IllegalStateException("一時停止中ではありません");
        }
        this.status = SubscriptionStatus.ACTIVE;
        this.nextDeliveryDate = nextDeliveryDate;
    }

    // getters / setters ...
}
```

このクラスは何を知っているか。

- **永続化の知識**: `@Entity`、`@Table`、`@Id`、`@Enumerated` — JPAのアノテーションが埋め込まれている
- **バリデーションの知識**: `@NotNull` — Bean Validationのアノテーションが埋め込まれている
- **状態管理の知識**: `suspend()` と `resume()` の中で状態チェックを行っている
- **可変状態**: `status` や `nextDeliveryDate` を `set` できる

これが「重いドメインモデル」の正体だ。ドメインモデルが、永続化・バリデーション・状態管理の知識を一身に背負っている。

## recordとsealed interfaceで書き直す

前章で示した `Subscription` を改めて見てほしい。

```java
public sealed interface Subscription
        permits Subscription.Active, Subscription.Suspended {

    UserId userId();
    OrderPlan plan();
    DeliveryFrequency frequency();

    record Active(
            SubscriptionId id,
            UserId userId,
            OrderPlan plan,
            DeliveryFrequency frequency,
            LocalDate nextDeliveryDate
    ) implements Subscription {}

    record Suspended(
            SubscriptionId id,
            UserId userId,
            OrderPlan plan,
            DeliveryFrequency frequency
    ) implements Subscription {}
}
```

このクラスが知っていることは何か。

- **ドメインの構造**: `Active` には `nextDeliveryDate` があり、`Suspended` にはない
- **それだけだ**

JPAのアノテーションはない。Bean Validationのアノテーションはない。状態チェックのロジックはない。可変フィールドもない。record は不変（immutable）なので、一度作られたら変更できない。

## 各知識の置き場所

「重いドメインモデル」が一箇所に集めていた知識は、それぞれ適切な場所に分散する。

### バリデーションの知識 → デコーダ（境界）

```java
// Ch4 で見たデコーダが入力の正当性を保証する
public static final Decoder<JsonNode, OrderPlan> ORDER_PLAN_DECODER =
        discriminate("planType", Map.of(
                "STANDARD", STANDARD_PLAN_DECODER,
                "PREMIUM",  PREMIUM_PLAN_DECODER,
                "CUSTOM",   CUSTOM_PLAN_DECODER
        ));
```

### 状態遷移の知識 → 振る舞いクラス

```java
// Ch7 で見た SubscriptionBehavior が状態遷移を担う
public Subscription.Suspended suspend(Subscription.Active active) { ... }
public Subscription.Active resume(Subscription.Suspended suspended, LocalDate nextDeliveryDate) { ... }
```

### 永続化の知識 → リポジトリ / jOOQ のマッピング

```java
// ドメインオブジェクトを永続化するマッピングはリポジトリ層が持つ
// ドメインモデル自身は @Entity アノテーションを持たない
public class SubscriptionRepository {
    public void save(Subscription subscription) {
        switch (subscription) {
            case Subscription.Active a -> jooq.insertInto(SUBSCRIPTIONS)
                    .set(SUBSCRIPTIONS.ID, a.id().value())
                    .set(SUBSCRIPTIONS.STATUS, "ACTIVE")
                    .set(SUBSCRIPTIONS.NEXT_DELIVERY_DATE, a.nextDeliveryDate())
                    .execute();
            case Subscription.Suspended s -> jooq.insertInto(SUBSCRIPTIONS)
                    .set(SUBSCRIPTIONS.ID, s.id().value())
                    .set(SUBSCRIPTIONS.STATUS, "SUSPENDED")
                    .set(SUBSCRIPTIONS.NEXT_DELIVERY_DATE, (LocalDate) null)
                    .execute();
        }
    }
}
```

## Bean Validation のFormクラスとの比較

Bean Validation を使った典型的な実装では、`OrderPlanForm` というクラスが存在した。

```java
@Data
@ValidOrderPlanForm
public class OrderPlanForm {
    @NotBlank
    @Pattern(regexp = "STANDARD|PREMIUM|CUSTOM")
    private String planType;

    private String mealSetId;      // STANDARD/PREMIUM のみ
    private Boolean includeFrozen; // PREMIUM のみ
    private List<String> mealIds;  // CUSTOM のみ
    // ...
}
```

これはドメインモデルではなく、**入力フォームの形**を表すクラスだ。バリデーションが通った後もこのクラスのまま残り、コントローラーでドメインオブジェクトに詰め替える必要があった。

Raoh のアプローチでは `OrderPlanForm` は存在しない。デコーダが `JsonNode` を受け取って直接 `OrderPlan` を返す。「入力フォームの形」と「ドメインの形」の間にギャップがない。

## まとめ: ドメインモデルが軽いとはどういうことか

| 項目 | 重いドメインモデル | 軽いドメインモデル（record） |
| --- | --- | --- |
| 永続化の知識 | `@Entity`、`@Column` を持つ | 持たない |
| バリデーションの知識 | `@NotNull`、`@Size` を持つ | 持たない |
| 状態チェック | メソッド内で `if` チェック | 型で排除済み（不要） |
| 可変性 | setter で変更可能 | record は不変 |
| テストのしやすさ | Spring Context や JPA が必要なことがある | ただの Java オブジェクト |

ドメインモデルが「ただの record」であることは、貧弱さではない。**関心の分離が適切に行われている証拠だ。** バリデーションは境界で、状態遷移は振る舞いクラスで、永続化はリポジトリで——それぞれの責務が明確に分かれているから、ドメインモデル自身はシンプルでいられる。

---

Part 3 のまとめ: Always-Valid Layer という概念を軸に置くと、「ビジネスロジック層」という曖昧な言葉に頼らずに設計の判断を下せる。境界でパースして型に変換し、型の保証の上で振る舞いを書く。ドメインモデルはその構造だけを表現すればよい。

次の Part 4 では、このドメインモデルをレイヤーをまたいでどう扱うか——「詰め替え」の問題に踏み込む。
