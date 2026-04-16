---
title: "ドメインモデルはただのrecordでいい"
---

## 「重いドメインモデル」の正体

DDDの文脈で「ドメインモデル」というとき、しばしば次のような実装が現れます。

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

このクラスは何を知っているでしょうか。

- **永続化の知識**: `@Entity`、`@Table`、`@Id`、`@Enumerated` — JPAのアノテーションが埋め込まれています
- **バリデーションの知識**: `@NotNull` — Bean Validationのアノテーションが埋め込まれています
- **状態管理の知識**: `suspend()` と `resume()` の中で状態チェックを行っています
- **可変状態**: `status` や `nextDeliveryDate` を `set` できます

これが「重いドメインモデル」の正体です。ドメインモデルが、永続化・バリデーション・状態管理の知識を一身に背負っています。

## recordとsealed interfaceで書き直す

前章で示した `Subscription` を改めて見てください。

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

このクラスが知っていることは何でしょうか。

- **ドメインの構造**: `Active` には `nextDeliveryDate` があり、`Suspended` にはありません
- **それだけです**

JPAのアノテーションはありません。Bean Validationのアノテーションはありません。状態チェックのロジックはありません。可変フィールドもありません。record は不変（immutable）なので、一度作られたら変更できません。

## 各知識の置き場所

「重いドメインモデル」が一箇所に集めていた知識は、それぞれ適切な場所に分散します。

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

Bean Validation を使った典型的な実装では、`OrderPlanForm` というクラスが存在しました。

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

これはドメインモデルではなく、**入力フォームの形**を表すクラスです。バリデーションが通った後もこのクラスのまま残り、コントローラーでドメインオブジェクトに詰め替える必要がありました。

Raoh のアプローチでは `OrderPlanForm` は存在しません。デコーダが `JsonNode` を受け取って直接 `OrderPlan` を返します。「入力フォームの形」と「ドメインの形」の間にギャップがありません。

## まとめ: ドメインモデルが軽いとはどういうことか

| 項目 | 重いドメインモデル | 軽いドメインモデル（record） |
| --- | --- | --- |
| 永続化の知識 | `@Entity`、`@Column` を持ちます | 持ちません |
| バリデーションの知識 | `@NotNull`、`@Size` を持ちます | 持ちません |
| 状態チェック | メソッド内で `if` チェック | 型で排除済み（不要） |
| 可変性 | setter で変更可能 | record は不変 |
| テストのしやすさ | Spring Context や JPA が必要なことがあります | ただの Java オブジェクト |

`Subscription` が `@Entity` を持たない理由は、永続化の知識が `SubscriptionRepositoryImpl` に移っているからです。`@NotNull` を持たない理由は、バリデーションの知識が `OrderPlanDecoder`（境界）に移っているからです。状態チェックの `if` を持たない理由は、状態遷移の知識が `SubscriptionBehavior`（振る舞いクラス）に移っているからです。ドメインモデルが軽い（これらの知識を持っていない）ことは、その知識が消えたのではなく、それぞれの責務を持つ適切なクラスに分散されたことを意味します。

ドメインモデルが「ただの record」であることは、貧弱さではありません。**関心の分離が適切に行われている証拠です。** バリデーションは境界で、状態遷移は振る舞いクラスで、永続化はリポジトリで——それぞれの責務が明確に分かれているから、ドメインモデル自身はシンプルでいられます。

---

Part 3 のまとめ: Always-Valid Layer という概念を軸に置くと、「ビジネスロジック層」という曖昧な言葉に頼らずに設計の判断を下せます。境界でパースして型に変換し、型の保証の上で振る舞いを書きます。ドメインモデルはその構造だけを表現すれば良いです。

次の Part 4 では、このドメインモデルをレイヤーをまたいでどう扱うか——「詰め替え」の問題に踏み込みます。
