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

このクラスが持っている知識は1つだけです。

- **ドメインの構造**: `Active` には `nextDeliveryDate` があり、`Suspended` にはありません

record は不変（immutable）です。一度作られたら変更できません。

## ドメインモデル貧血症との関係

`Active` や `Suspended` がアクセッサしか持たないのを見て、「これはドメインモデル貧血症ではないか」と感じる方がいるかもしれません。

**ドメインモデル貧血症**（Martin Fowler 命名）は、「フィールドの Getter/Setter しか持たず、業務知識がドメインオブジェクトの外に散らばっている状態」を指します。Fowler はこの状態を「オブジェクト指向設計の根本的な誤り」と批判しており、業務ロジックがドメインオブジェクトではなくサービス層やユーティリティクラスに集中することで、ドメインモデルが単なるデータ構造に成り下がってしまうことを問題視しています。

本書はこの定義を否定するものではありませんが、型の観点から一歩踏み込みます。**「メソッドがない」だけでは不十分であり、業務上ありえない操作を型が許してしまうこと**もまた問題だと考えます。

`Subscription` を1つのクラスで表現し、`suspend()` と `resume()` を持たせた場合を考えます。

```java
// ACTIVE 状態の subscription に resume() を呼べてしまう
subscription.resume(nextDate); // コンパイルを通る。実行時に例外
```

メソッドを持たせても、「アクティブな定期便を再開する」という業務上ありえない操作が型として存在し続けます。`if` チェックで弾くのは、問題を実行時に先送りしているだけです。

`sealed interface` で `Active` と `Suspended` を別の型にすると、`resume()` は `Suspended` しか受け取れません。

```java
// SubscriptionBehavior.resume() のシグネチャ
public Subscription.Active resume(Subscription.Suspended suspended, LocalDate nextDeliveryDate) { ... }
```

`Active` を渡そうとするとコンパイルエラーになります。「ありえない操作」が型として存在しないため、防御コード自体が不要になります。

貧血症かどうかは、**業務上異なる状態を異なる型として定義できているかどうか**にかかっています。振る舞いがドメインモデル自身に実装されているかどうかは、本質ではありません。関数型言語でドメインモデルを書くとき、振る舞いはモデルとは別の関数として実装されますが、それは貧血症とは呼びません。

`sealed interface` × `record` は、業務上の状態の違いを型で表現します。それ自体がドメイン知識の実装です。

## 各知識の置き場所

一箇所に集まっていた知識は、それぞれ適切な場所に分散します。バリデーション・状態遷移・永続化の3つがそれぞれどこに移動するかを見ていきます。

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
    // 注: このサンプルは INSERT のみを示しています。
    // 実際の save() は新規作成と更新の両方を扱うため、
    // jooq.insertInto(...).onConflictDoUpdate() 等で UPSERT にするか、
    // 新規/更新を明示的に分けてください。
    public void save(Subscription subscription) {
        switch (subscription) {
            case Subscription.Active a -> jooq.insertInto(SUBSCRIPTIONS)
                    .set(SUBSCRIPTIONS.ID, a.id().value())
                    .set(SUBSCRIPTIONS.STATUS, "ACTIVE")
                    .set(SUBSCRIPTIONS.NEXT_DELIVERY_DATE, a.nextDeliveryDate())
                    .execute();
            // Suspended には nextDeliveryDate フィールドがないため、
            // NEXT_DELIVERY_DATE 列はセットしない（DB 側の DEFAULT NULL に任せる）
            case Subscription.Suspended s -> jooq.insertInto(SUBSCRIPTIONS)
                    .set(SUBSCRIPTIONS.ID, s.id().value())
                    .set(SUBSCRIPTIONS.STATUS, "SUSPENDED")
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

これはドメインモデルではなく、**入力フォームの形**を表すクラスです。バリデーションが通った後もこのクラスのまま残り、コントローラーでドメインモデルに詰め替える必要がありました。

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

### JPA を使いながら分離する

本書のサンプルは永続化に jOOQ を使いますが、これは「ドメインモデルから `@Entity` を取り除く」ための最短経路を示すためです。**JPA を使い続けることと、ドメインモデルを record にすることは両立します。**

具体的には、JPA 用の `SubscriptionJpaEntity`（永続化専用のクラス）とドメインモデルの `Subscription`（sealed interface + record）を別クラスとして定義し、リポジトリ実装がその間で相互変換する構成です。

```java
// 永続化専用のクラス。ここに @Entity が付く
@Entity
@Table(name = "subscriptions")
public class SubscriptionJpaEntity {
    @Id private String id;
    @Enumerated(EnumType.STRING) private SubscriptionStatus status;
    private LocalDate nextDeliveryDate;
    // ...
}

// ドメインモデル側は素の record のまま
public sealed interface Subscription permits Subscription.Active, Subscription.Suspended { /* ... */ }

// リポジトリ実装が JpaEntity ↔ ドメインモデルを変換する
public class SubscriptionRepositoryImpl implements SubscriptionRepository {
    public void save(Subscription subscription) {
        SubscriptionJpaEntity entity = toJpaEntity(subscription);
        em.merge(entity);
    }
}
```

> **注**: `toJpaEntity()` を含む完全な実装は、本書のコード断片を実装したプロジェクトの `SubscriptionRepositoryImpl` に相当します。

相互変換の記述量は増えますが、JPA が提供する変更追跡・遅延ロード・キャッシュといった機能を捨てずに、ドメインモデルを関心から解放できます。値オブジェクト（`UserId` など）の変換は `@Converter` や `@Embeddable` で吸収できます。

本書が jOOQ を採用しているのは「永続化知識を混入させない」原理を最短で示すためであり、「jOOQ に移行せよ」という主張ではありません。JPA を残したままでも、本書のドメインモデル設計は機能します。

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

これが「ドメインモデルが軽い」ことの実際的な価値です。Spring Context や JPA の設定を伴うテストと比較して、ドメインの振る舞いテストは明らかに高速に実行でき、各テストを独立した単位として扱えます。テストの実行時間が短くなること以上に重要なのは、「依存するフレームワークが少ないほどテストの書き始めが軽い」ことです。

---

本章のまとめ: `@Entity`・`@NotNull`・状態チェックがドメインモデルに混在していた理由は、各知識の置き場所が定まっていなかったことにあります。バリデーションをデコーダへ、状態遷移を振る舞いクラスへ、永続化をリポジトリへ分散させることで、ドメインモデルは構造だけを表現する「ただの record」になります。この軽さは貧弱さではなく、関心の分離が適切に行われている証拠です。

次の9〜11章では、このドメインモデルをレイヤーをまたいでどう扱うか——「詰め替え」の問題に踏み込みます。
