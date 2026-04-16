---
title: "Bean Validationの設計と限界"
---

## ミールス宅配サービスの注文ドメイン

本書では全章を通じて「ミールス宅配サービス」という架空のサービスを題材にします。このサービスでは、ユーザーが定期便を契約し、毎週または隔週でミールセット（食材セット）が届きます。

注文フローの入力として、以下の3種類のプランがあります。

```java
// プランは3種類。それぞれ必要な情報が異なる
public sealed interface OrderPlan
        permits StandardPlan, PremiumPlan, CustomPlan {
}

// スタンダードプラン: ミールセットを選ぶだけ
public record StandardPlan(
        MealSetId mealSetId,
        DeliveryFrequency frequency
) implements OrderPlan {}

// プレミアムプラン: ミールセット + 冷凍オプション
public record PremiumPlan(
        MealSetId mealSetId,
        DeliveryFrequency frequency,
        boolean includeFrozen
) implements OrderPlan {}

// カスタムプラン: 食材を個別に選ぶ
public record CustomPlan(
        List<MealId> meals,
        DeliveryFrequency frequency,
        LocalDate startDate   // カスタムは開始日を指定できます
) implements OrderPlan {}
```

プランの種類によって「必要なフィールド」が異なります。これがバリデーションを難しくする核心的な問題です。

## Bean Validationによる実装

まずBean Validationで実装してみましょう。Spring MVC + Jakarta Bean Validationの標準的なやり方です。

### フォームクラス

```java
@Data
@ValidOrderPlanForm   // カスタムバリデーションアノテーション
public class OrderPlanForm {

    // 共通フィールド
    @NotBlank(message = "プランタイプは必須です")
    @Pattern(regexp = "STANDARD|PREMIUM|CUSTOM",
             message = "プランタイプはSTANDARD、PREMIUM、CUSTOMのいずれかです")
    private String planType;

    @NotNull(message = "配送頻度は必須です")
    @Pattern(regexp = "WEEKLY|BIWEEKLY",
             message = "配送頻度はWEEKLYまたはBIWEEKLYです")
    private String frequency;

    // STANDARD / PREMIUM で使うフィールド
    private String mealSetId;

    // PREMIUM のみで使うフィールド
    private Boolean includeFrozen;

    // CUSTOM のみで使うフィールド
    private List<String> mealIds;
    private LocalDate startDate;
}
```

フラットな1クラスに、3つのプランで使うフィールドがすべて同居しています。`mealSetId` はSTANDARDとPREMIUMで使いますが、CUSTOMでは使いません。`mealIds` と `startDate` はCUSTOMでしか使いません。各フィールドがどのプランで使われるかは、コメント（`// STANDARD / PREMIUM で使うフィールド`）でしか示されていません。型システムがこの関係を保証していないため、コメントが古くなったとき・コメントを見落としたときに誤った使い方が発生します。

### カスタムバリデーター

`@NotBlank` や `@NotNull` といったアノテーションは、単一フィールドの制約には使えます。しかし「プランタイプがSTANDARDのときは `mealSetId` が必須」という**条件付き必須**はアノテーションでは表現できません。これを書くには `ConstraintValidator` が必要になります。

```java
public class OrderPlanFormValidator
        implements ConstraintValidator<ValidOrderPlanForm, OrderPlanForm> {

    @Override
    public boolean isValid(OrderPlanForm form,
                           ConstraintValidatorContext context) {
        if (form == null || form.getPlanType() == null) {
            return true;
        }
        context.disableDefaultConstraintViolation();

        return switch (form.getPlanType()) {
            case "STANDARD" -> validateStandard(form, context);
            case "PREMIUM"  -> validatePremium(form, context);
            case "CUSTOM"   -> validateCustom(form, context);
            default -> true;
        };
    }

    private boolean validateStandard(OrderPlanForm form,
                                     ConstraintValidatorContext context) {
        boolean valid = true;
        if (form.getMealSetId() == null || form.getMealSetId().isBlank()) {
            addViolation(context, "mealSetId", "ミールセットは必須です");
            valid = false;
        }
        return valid;
    }

    private boolean validatePremium(OrderPlanForm form,
                                    ConstraintValidatorContext context) {
        boolean valid = true;
        if (form.getMealSetId() == null || form.getMealSetId().isBlank()) {
            addViolation(context, "mealSetId", "ミールセットは必須です");
            valid = false;
        }
        if (form.getIncludeFrozen() == null) {
            addViolation(context, "includeFrozen", "冷凍オプションの選択は必須です");
            valid = false;
        }
        return valid;
    }

    private boolean validateCustom(OrderPlanForm form,
                                   ConstraintValidatorContext context) {
        boolean valid = true;
        if (form.getMealIds() == null || form.getMealIds().isEmpty()) {
            addViolation(context, "mealIds", "食材を1つ以上選択してください");
            valid = false;
        }
        if (form.getStartDate() == null) {
            addViolation(context, "startDate", "開始日は必須です");
            valid = false;
        } else if (form.getStartDate().isBefore(LocalDate.now().plusDays(3))) {
            addViolation(context, "startDate", "開始日は3日以上先の日付を指定してください");
            valid = false;
        }
        return valid;
    }

    private void addViolation(ConstraintValidatorContext context,
                              String property, String message) {
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(property)
                .addConstraintViolation();
    }
}
```

### コントローラー

```java
@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping
    public ResponseEntity<?> create(
            @Validated @RequestBody OrderPlanForm form,
            BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            List<Map<String, String>> errors = bindingResult.getFieldErrors().stream()
                    .map(e -> Map.of(
                            "field", e.getField(),
                            "message", e.getDefaultMessage()))
                    .toList();
            return ResponseEntity.badRequest().body(Map.of("errors", errors));
        }

        // バリデーション通過後もまだ OrderPlanForm のまま。
        // どのプランかを判定するために再度 switch が必要になる。
        OrderPlan plan = switch (form.getPlanType()) {
            case "STANDARD" -> new StandardPlan(
                    new MealSetId(form.getMealSetId()),
                    DeliveryFrequency.valueOf(form.getFrequency())
            );
            case "PREMIUM" -> new PremiumPlan(
                    new MealSetId(form.getMealSetId()),
                    DeliveryFrequency.valueOf(form.getFrequency()),
                    form.getIncludeFrozen()
            );
            case "CUSTOM" -> new CustomPlan(
                    form.getMealIds().stream().map(MealId::new).toList(),
                    DeliveryFrequency.valueOf(form.getFrequency()),
                    form.getStartDate()
            );
            default -> throw new IllegalStateException("unreachable");
        };

        String subscriptionId = subscriptionService.create(plan);
        return ResponseEntity.ok(Map.of("subscriptionId", subscriptionId));
    }
}
```

## 何が問題か

このBean Validationによる実装には、構造的な問題が3つあります。

### 問題1: フォームクラスに「ありえない状態」が存在する

`OrderPlanForm` はプランの種類にかかわらず全フィールドを持ちます。つまり「STANDARDプランなのに `mealIds` が入っている」「CUSTOMプランなのに `mealSetId` も `mealIds` も入っていない」といった状態が、クラスの構造上は許されてしまいます。

バリデーターがそれを弾いてくれるとはいえ、**型を見ただけでは不正な状態が排除されているとは分かりません**。

### 問題2: バリデーション通過後も型が曖昧なまま

`@Validated` で検証が通ったとしても、コントローラーの手元にあるのは相変わらず `OrderPlanForm` です。「どのプランか」を知るには `getPlanType()` を呼んで文字列を確認するしかありません。

そのため、コントローラーでドメインオブジェクトを組み立てるための **2回目の `switch` 文** が必要になります。バリデーターの `switch` と合わせて、同じ分岐ロジックがコードベースに2箇所存在することになります。

### 問題3: 構造の深さに比例してバリデーターが肥大化する

今回の例はプランが3種類で浅い構造ですが、実際の業務では入れ子の分岐が生まれやすいです。たとえば「CUSTOMプランの場合、配送エリアが北海道・沖縄なら追加料金の確認が必要」といった条件が加わると、バリデーターのネストがさらに深くなります。

Bean Validationはこの入れ子の分岐に対応する仕組みを持っていません。`ConstraintValidator` は単一の `isValid()` メソッドで全分岐を処理する設計であり、分岐を別クラスに分離する公式のメカニズムがありません。グループ切り替え（`@GroupSequence`）で部分的な対応は可能ですが、プランタイプに応じた条件付きバリデーション（「`planType` が `CUSTOM` のときだけ `startDate` を検証する」）には使えません。結果として `ConstraintValidator` の中で `if` と `switch` の手続きコードが膨らんでいきます。

---

これらの問題の根っこは共通しています。**入力の形（フォームクラス）とドメインの型（sealed interface）が別物として存在し、その変換が2ステップに分かれていること**です。次章では、この2ステップを1ステップにまとめるアプローチを見ていきます。
