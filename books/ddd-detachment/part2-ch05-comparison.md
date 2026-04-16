---
title: "二つのアプローチの比較"
---

## 同じドメイン、二つの実装

前章までで、ミールス宅配サービスの注文フローを Bean Validation と Raoh の両方で実装しました。ここで並べて比較します。

### コード量と構造

| | Bean Validation | Raoh |
| --- | --- | --- |
| 入力を受け取るクラス | `OrderPlanForm`（全フィールド同居） | なし（`JsonNode` を直接受け取ります） |
| バリデーションの記述場所 | `@ValidOrderPlanForm` アノテーション + `OrderPlanFormValidator` クラス | `ORDER_PLAN_DECODER` の定義 |
| 分岐の記述 | バリデーターに1回 + コントローラーに1回 | `discriminate` に1回のみ |
| バリデーション後の型 | `OrderPlanForm`（文字列の `planType` で判別） | `OrderPlan`（sealed interfaceの具体型） |
| エラーパス | `addPropertyNode()` で手動構築 | 自動構成 |

### バリデーション後の世界の違い

これが最も本質的な差です。

**Bean Validation の場合**、`@Validated` を通過した後もコントローラーが持っているのは `OrderPlanForm` です。「バリデーション済み」という事実はどこにも型として表れません。コンパイラはこのオブジェクトが検証済みかどうかを知りません。

```java
// バリデーション通過後でも planType は String のまま
// コンパイラには「これが検証済みの正しい値」とは分からない
String planType = form.getPlanType(); // "STANDARD" | "PREMIUM" | "CUSTOM" | ...?
```

**Raoh の場合**、`decode()` が `Ok` を返した時点で、中に入っている値は `OrderPlan` です。Java の sealed interface の exhaustive な `switch` と組み合わせると、コンパイラが「すべてのケースを処理しているか」を確認してくれます。

```java
// plan は OrderPlan 型。コンパイラが具体型を追跡できる
switch (plan) {
    case StandardPlan p  -> ...  // mealSetId と frequency が確実に存在する
    case PremiumPlan p   -> ...  // includeFrozen が確実に存在する
    case CustomPlan p    -> ...  // meals と startDate が確実に存在する
    // ここに case を書き忘れるとコンパイルエラーになる
}
```

新しいプランが追加されたとき（たとえば `TrialPlan` が増えたとき）を想像してください。Raoh では sealed interface に `TrialPlan` を追加した瞬間に、`switch` を書いているすべての箇所でコンパイルエラーが出ます。対応漏れをコンパイラが教えてくれます。Bean Validation ではそういった仕組みはありません。

## Bean Validation が適している場面

Bean Validation が力を発揮するのは、**フラットで多態性のないフォーム**です。

```java
// 配送先住所の変更フォーム。全フィールドが常に必須で分岐がない
@Data
public class AddressForm {
    @NotBlank
    @Size(max = 7)
    private String postalCode;

    @NotBlank
    @Size(max = 10)
    private String prefecture;

    @NotBlank
    @Size(max = 50)
    private String city;

    @NotBlank
    @Size(max = 100)
    private String street;
}
```

このような場合、Bean Validation は極めて簡潔です。アノテーションを読むだけでバリデーションルールが一目でわかります。Raoh で書き直す理由はほとんどありません。

## Raoh が適している場面

Raoh が力を発揮するのは、**プランの種類・状態・タイプによって構造が変わる入力**です。

- 「支払い方法によって必要なフィールドが変わる」
- 「ユーザーの種別（個人・法人）によって入力項目が異なる」
- 「商品カテゴリによってオプション項目の構造が違う」

こういった入れ子の分岐がある場合、Bean Validation の `ConstraintValidator` はすぐに肥大化します。Raoh の `discriminate` の入れ子はドメインの構造をそのまま反映するので、コードがドキュメントの役割も果たします。

## 選択の判断基準

まず入力の多態性の有無（一次条件）で判断し、その上で補足条件を考慮します。

### 一次条件（まずここで判断）

| 条件 | 選択 |
| --- | --- |
| フラットな構造で、全フィールドが常に同じ制約を持つ | Bean Validation |
| フィールドの必須/任意がタイプや状態によって変わる | Raoh |

### 補足条件（一次条件と組み合わせて考慮）

| 条件 | 選択 |
| --- | --- |
| バリデーション後に sealed interface の具体型が必要 | Raoh |
| 既存の Spring MVC プロジェクトへの最小限の追加 | Bean Validation |
| 多態的な構造を型安全に扱いたい | Raoh |

この2つは競合するものではありません。同じアプリケーションの中で、シンプルなフォームには Bean Validation を、複雑な多態的入力には Raoh を使い分けることができます。

---

Part 2 のまとめとして: Bean Validation の問題は Bean Validation 自体にあるのではなく、**多態的な入力を「フラットなフォームクラス + 手続きValidator」という構造で表現しようとすること**にあります。Raoh はその構造的ミスマッチを解消し、入力の形をそのままドメインの型として受け取れるようにします。

次のPart 3では、バリデーションを通過した後の世界——Always-Valid Layer——について詳しく見ていきます。
