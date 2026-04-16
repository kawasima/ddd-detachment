---
title: "よく見かけるSpring Boot + DDD構成"
---

## ミールス宅配サービスのドメイン

この本を通じて使うドメインを最初に示す。ミールス宅配サービス（架空）の注文と定期便管理だ。

### 注文（Order）

利用者はプランを選んで注文する。プランには3種類ある。

- **スタンダードプラン**: 食事セットを1つ選ぶ。配送頻度を指定する。
- **プレミアムプラン**: 食事セットを1つ選ぶ。冷凍食材を含めるか選択できる。配送頻度を指定する。
- **カスタムプラン**: 食材を複数選んで組み合わせる。配送頻度と開始日を指定する（開始日は3日以上先）。

### 定期便（Subscription）

注文が完了すると定期便が作られる。定期便には「アクティブ」と「一時停止」の2つの状態がある。

- **アクティブ**: 定期的に食材が届く。次回配送日を持つ。
- **一時停止**: 配送を止めている状態。次回配送日は持たない。

アクティブな定期便を「一時停止」にでき、一時停止した定期便を「再開」（アクティブに戻す）できる。

---

このドメインを、典型的な Spring Boot + DDD 構成で実装するとどうなるか。

## 典型的なレイヤー構成

よく見かける構成はこうだ。

```text
com.example.mealse
├── presentation      ← Controller + Form
│   └── OrderController
│   └── OrderPlanForm
├── application       ← UseCase（Service）
│   └── OrderApplicationService
│   └── CreateOrderCommand
├── domain            ← ドメインモデル + 振る舞い
│   └── Subscription  ← @Entity が付いている
│   └── OrderPlan     ← フラグで状態を持つ
└── infrastructure    ← Repository 実装
    └── SubscriptionJpaRepository
```

各レイヤーが独自のモデルを持ち、UseCase の入出力には専用の Command/Data オブジェクトを使う。これが Full Mapping だ。

## 各レイヤーのコード

### プレゼンテーション層（Form + Controller）

```java
@Data
@ValidOrderPlanForm   // カスタムバリデーション
public class OrderPlanForm {
    @NotBlank
    @Pattern(regexp = "STANDARD|PREMIUM|CUSTOM")
    private String planType;

    private String mealSetId;       // STANDARD / PREMIUM のみ
    private Boolean includeFrozen;  // PREMIUM のみ
    private List<String> mealIds;   // CUSTOM のみ
    private String frequency;
    private LocalDate startDate;    // CUSTOM のみ
}
```

```java
@PostMapping("/orders")
public ResponseEntity<?> createOrder(
        @Valid @RequestBody OrderPlanForm form,
        BindingResult bindingResult) {
    if (bindingResult.hasErrors()) {
        return ResponseEntity.badRequest().body(bindingResult.getAllErrors());
    }
    // Form → Command への詰め替え（1回目）
    CreateOrderCommand command = new CreateOrderCommand(
            currentUserId(),
            form.getPlanType(),
            form.getMealSetId(),
            form.getIncludeFrozen(),
            form.getMealIds(),
            form.getFrequency(),
            form.getStartDate()
    );
    orderApplicationService.createOrder(command);
    return ResponseEntity.status(201).build();
}
```

### アプリケーション層（UseCase + Command）

```java
// UseCase の入力を表す DTO
public class CreateOrderCommand {
    private final String userId;
    private final String planType;
    private final String mealSetId;
    private final Boolean includeFrozen;
    private final List<String> mealIds;
    private final String frequency;
    private final LocalDate startDate;
    // コンストラクタ + getter ...
}
```

```java
@Service
public class OrderApplicationService {
    public void createOrder(CreateOrderCommand command) {
        // Command → ドメインモデルへの詰め替え（2回目）
        OrderPlan plan = buildPlan(command);
        String subscriptionId = UUID.randomUUID().toString();
        LocalDate nextDelivery = LocalDate.now().plusWeeks(1);
        Subscription subscription = new Subscription();
        subscription.setId(subscriptionId);
        subscription.setUserId(command.getUserId());
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setNextDeliveryDate(nextDelivery);
        // ...
        subscriptionRepository.save(subscription);
    }

    private OrderPlan buildPlan(CreateOrderCommand command) {
        // planType の文字列で分岐して詰め替え
        return switch (command.getPlanType()) {
            case "STANDARD" -> new OrderPlan(
                    command.getMealSetId(), command.getFrequency(), null, null, null);
            case "PREMIUM" -> new OrderPlan(
                    command.getMealSetId(), command.getFrequency(),
                    command.getIncludeFrozen(), null, null);
            case "CUSTOM" -> new OrderPlan(
                    null, command.getFrequency(), null,
                    command.getMealIds(), command.getStartDate());
            default -> throw new IllegalArgumentException("Unknown plan type");
        };
    }
}
```

### ドメイン層（バリデーションと永続化の知識が混入）

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

    private LocalDate nextDeliveryDate; // ACTIVE のときのみ意味を持つ

    protected Subscription() {}

    public void suspend() {
        if (this.status == SubscriptionStatus.SUSPENDED) {
            throw new IllegalStateException("すでに一時停止中です");
        }
        this.status = SubscriptionStatus.SUSPENDED;
        this.nextDeliveryDate = null;
    }

    // getters / setters ...
}
```

```java
// プランもフラットなクラス。プランの種類によらず全フィールドを持つ
public class OrderPlan {
    private String mealSetId;       // STANDARD / PREMIUM のみ使う
    private String frequency;
    private Boolean includeFrozen;  // PREMIUM のみ使う
    private List<String> mealIds;   // CUSTOM のみ使う
    private LocalDate startDate;    // CUSTOM のみ使う
    // ...
}
```

## 何が起きているか

コードを追うと、次のことが見えてくる。

**詰め替えの回数**: HTTP リクエストから DB 書き込みまでに、少なくとも3回の詰め替えが発生している。`OrderPlanForm` → `CreateOrderCommand` → `Subscription` → JPA がテーブルにマッピング、だ。

**型の曖昧さ**: `OrderPlanForm` でバリデーションが通った後も、型は `OrderPlanForm` のままだ。`planType = "STANDARD"` のとき `mealSetId` が null かどうかは、実行時にしかわからない。バリデーションが通過した後も、防御的な null チェックや `planType` による分岐が各所に現れる。

**ドメインモデルの重さ**: `Subscription` クラスは `@Entity`、`@Table`、`@NotNull`、状態チェックのロジックをすべて持っている。データベースの知識と Jakarta Validationの知識とドメインロジックが1つのクラスに混在している。

次章では、この構成の問題を整理する。

---

この構成自体が「悪い」わけではない。小規模なCRUDアプリケーションでは十分に機能する。問題は、**この構成に対して批判的に考えることなく、複雑なドメインにも同じパターンを適用し続けること**だ。
