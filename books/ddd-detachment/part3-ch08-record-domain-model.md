---
title: "ドメインモデルから関心を分離する"
---

## 複数の関心を背負ったドメインモデル

Spring Boot + JPA でドメインモデルを実装するとき、しばしば次のような形になります。

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

このクラスは、永続化・バリデーション・状態管理という複数の関心を一身に背負っています。

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

record は不変（immutable）です。一度作られたら変更できません。

## 各知識の置き場所

一箇所に集まっていた知識は、それぞれ適切な場所に分散します。

### バリデーションの知識 → デコーダ（境界）

```java
// 4章で見たデコーダが入力の正当性を保証する
public static final Decoder<JsonNode, OrderPlan> ORDER_PLAN_DECODER =
        discriminate("planType", Map.of(
                "STANDARD", STANDARD_PLAN_DECODER,
                "PREMIUM",  PREMIUM_PLAN_DECODER,
                "CUSTOM",   CUSTOM_PLAN_DECODER
        ));
```

### 状態遷移の知識 → 振る舞いクラス

```java
// 7章で見た SubscriptionBehavior が状態遷移を担う
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

| 項目 | 関心が混在したドメインモデル | 関心を分離したドメインモデル（record） |
| --- | --- | --- |
| 永続化の知識 | `@Entity`、`@Column` をクラス自身が持つ | リポジトリ層が持つ |
| バリデーションの知識 | `@NotNull`、`@Size` をクラス自身が持つ | デコーダ（境界）が持つ |
| 状態チェック | メソッド内で `if` チェック | 型で排除済み（不要） |
| 可変性 | setter で変更可能 | record は不変 |
| テストのしやすさ | Spring Context や JPA が必要なことがある | ただの Java オブジェクトとして生成できる |

`Subscription` が `@Entity` を持たない理由は、永続化の知識が `SubscriptionRepositoryImpl` に移っているからです。`@NotNull` を持たない理由は、バリデーションの知識が `OrderPlanDecoder`（境界）に移っているからです。状態チェックの `if` を持たない理由は、状態遷移の知識が `SubscriptionBehavior`（振る舞いクラス）に移っているからです。ドメインモデルが軽い（これらの知識を持っていない）ことは、その知識が消えたのではなく、それぞれの責務を持つ適切なクラスに分散されたことを意味します。

ドメインモデルが「ただの record」であることは、貧弱さではありません。**関心の分離が適切に行われている証拠です。** バリデーションは境界で、状態遷移は振る舞いクラスで、永続化はリポジトリで——それぞれの責務が明確に分かれているから、ドメインモデル自身はシンプルでいられます。

## テストのしやすさを実感する

「テストのしやすさは設計のバロメーター」と述べました。実際にどう違うか、テストコードで確認します。

### ドメインモデルのテスト

`Subscription` が「ただの record」であれば、テストは純粋な Java オブジェクトの生成だけで済みます。Spring Context も JPA も不要です。

```java
class SubscriptionBehaviorTest {

    private final SubscriptionBehavior behavior = new SubscriptionBehavior();

    @Test
    void アクティブな定期便を一時停止できる() {
        Subscription.Active active = new Subscription.Active(
                new SubscriptionId("sub-1"),
                new UserId("user-1"),
                new OrderPlan.StandardPlan(new MealSetId("set-1"), DeliveryFrequency.WEEKLY),
                DeliveryFrequency.WEEKLY,
                LocalDate.of(2025, 1, 10)
        );

        Subscription.Suspended suspended = behavior.suspend(active);

        assertThat(suspended.id()).isEqualTo(active.id());
        assertThat(suspended.userId()).isEqualTo(active.userId());
        // nextDeliveryDate フィールド自体が Suspended には存在しない（コンパイル時に保証）
    }

    @Test
    void 一時停止した定期便を再開できる() {
        Subscription.Suspended suspended = new Subscription.Suspended(
                new SubscriptionId("sub-1"),
                new UserId("user-1"),
                new OrderPlan.StandardPlan(new MealSetId("set-1"), DeliveryFrequency.WEEKLY),
                DeliveryFrequency.WEEKLY
        );
        LocalDate nextDelivery = LocalDate.of(2025, 1, 17);

        Subscription.Active resumed = behavior.resume(suspended, nextDelivery);

        assertThat(resumed.nextDeliveryDate()).isEqualTo(nextDelivery);
    }
}
```

`new Subscription.Active(...)` だけでテストが始まります。フレームワークのセットアップは一切不要です。

### デコーダのテスト

デコーダのテストも同様にシンプルです。`ObjectMapper` で JSON を作り、デコーダに渡すだけです。

```java
class OrderPlanDecoderTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void スタンダードプランを正しくデコードできる() throws Exception {
        JsonNode body = mapper.readTree("""
                {
                  "planType": "STANDARD",
                  "mealSetId": "set-1",
                  "frequency": "WEEKLY"
                }
                """);

        Result<OrderPlan> result = OrderPlanDecoder.ORDER_PLAN_DECODER.decode(body);

        assertThat(result).isInstanceOf(Ok.class);
        OrderPlan plan = ((Ok<OrderPlan>) result).value();
        assertThat(plan).isInstanceOf(OrderPlan.StandardPlan.class);
    }

    @Test
    void 必須フィールドが欠けているとエラーになる() throws Exception {
        JsonNode body = mapper.readTree("""
                {
                  "planType": "STANDARD",
                  "frequency": "WEEKLY"
                }
                """);
        // mealSetId が抜けている

        Result<OrderPlan> result = OrderPlanDecoder.ORDER_PLAN_DECODER.decode(body);

        assertThat(result).isInstanceOf(Err.class);
        List<Issue> issues = ((Err<OrderPlan>) result).issues().asList();
        assertThat(issues).anyMatch(i -> i.path().toString().contains("mealSetId"));
    }

    @Test
    void カスタムプランの開始日が3日未満だとエラーになる() throws Exception {
        String tooSoon = LocalDate.now().plusDays(1).toString();
        JsonNode body = mapper.readTree("""
                {
                  "planType": "CUSTOM",
                  "meals": ["meal-1"],
                  "frequency": "WEEKLY",
                  "startDate": "%s"
                }
                """.formatted(tooSoon));

        Result<OrderPlan> result = OrderPlanDecoder.ORDER_PLAN_DECODER.decode(body);

        assertThat(result).isInstanceOf(Err.class);
        List<Issue> issues = ((Err<OrderPlan>) result).issues().asList();
        assertThat(issues).anyMatch(i -> i.path().toString().contains("startDate"));
    }
}
```

テストが通るかどうか確認するために Spring を起動する必要はありません。各テストは独立した単位として高速に実行できます。

これが「ドメインモデルが軽い」ことの実際的な価値です。Spring Context が必要だったときは、ドメインのテストに数秒〜十数秒かかっていたはずです。`record` ベースのドメインモデルでは、ミリ秒単位で終わります。

---

Part 3 のまとめ: Always-Valid Layer という概念を軸に置くと、「ビジネスロジック層」という曖昧な言葉に頼らずに設計の判断を下せます。境界でパースして型に変換し、型の保証の上で振る舞いを書きます。ドメインモデルはその構造だけを表現すれば良いです。

次の Part 4 では、このドメインモデルをレイヤーをまたいでどう扱うか——「詰め替え」の問題に踏み込みます。
