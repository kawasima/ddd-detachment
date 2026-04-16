---
title: "二つのアプローチの比較"
---

## 同じドメイン、二つの実装

前章までで、ミールス宅配サービスの注文フローを Bean Validation と Raoh の両方で実装した。ここで並べて比較する。

### コード量と構造

| | Bean Validation | Raoh |
| --- | --- | --- |
| 入力を受け取るクラス | `OrderPlanForm`（全フィールド同居） | なし（`JsonNode` を直接受け取る） |
| バリデーションの記述場所 | `@ValidOrderPlanForm` アノテーション + `OrderPlanFormValidator` クラス | `ORDER_PLAN_DECODER` の定義 |
| 分岐の記述 | バリデーターに1回 + コントローラーに1回 | `discriminate` に1回のみ |
| バリデーション後の型 | `OrderPlanForm`（文字列の `planType` で判別） | `OrderPlan`（sealed interfaceの具体型） |
| エラーパス | `addPropertyNode()` で手動構築 | 自動構成 |

### バリデーション後の世界の違い

これが最も本質的な差だ。

**Bean Validation の場合**、`@Validated` を通過した後もコントローラーが持っているのは `OrderPlanForm` だ。「バリデーション済み」という事実はどこにも型として表れない。コンパイラはこのオブジェクトが検証済みかどうかを知らない。

```java
// バリデーション通過後でも planType は String のまま
// コンパイラには「これが検証済みの正しい値」とは分からない
String planType = form.getPlanType(); // "STANDARD" | "PREMIUM" | "CUSTOM" | ...?
```

**Raoh の場合**、`decode()` が `Ok` を返した時点で、中に入っている値は `OrderPlan` だ。Java の sealed interface の exhaustive な `switch` と組み合わせると、コンパイラが「すべてのケースを処理しているか」を確認してくれる。

```java
// plan は OrderPlan 型。コンパイラが具体型を追跡できる
switch (plan) {
    case StandardPlan p  -> ...  // mealSetId と frequency が確実に存在する
    case PremiumPlan p   -> ...  // includeFrozen が確実に存在する
    case CustomPlan p    -> ...  // meals と startDate が確実に存在する
    // ここに case を書き忘れるとコンパイルエラーになる
}
```

新しいプランが追加されたとき（たとえば `TrialPlan` が増えたとき）を想像してほしい。Raoh では sealed interface に `TrialPlan` を追加した瞬間に、`switch` を書いているすべての箇所でコンパイルエラーが出る。対応漏れをコンパイラが教えてくれる。Bean Validation ではそういった仕組みはない。

## Bean Validation が適している場面

Bean Validation が力を発揮するのは、**フラットで多態性のないフォーム**だ。

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

このような場合、Bean Validation は極めて簡潔だ。アノテーションを読むだけでバリデーションルールが一目でわかる。Raoh で書き直す理由はほとんどない。

## Raoh が適している場面

Raoh が力を発揮するのは、**プランの種類・状態・タイプによって構造が変わる入力**だ。

- 「支払い方法によって必要なフィールドが変わる」
- 「ユーザーの種別（個人・法人）によって入力項目が異なる」
- 「商品カテゴリによってオプション項目の構造が違う」

こういった入れ子の分岐がある場合、Bean Validation の `ConstraintValidator` はすぐに肥大化する。Raoh の `discriminate` の入れ子はドメインの構造をそのまま反映するので、コードがドキュメントの役割も果たす。

## 選択の判断基準

| 条件 | 選択 |
| --- | --- |
| フラットな構造で、全フィールドが常に同じ制約を持つ | Bean Validation |
| フィールドの必須/任意がタイプや状態によって変わる | Raoh |
| バリデーション後に sealed interface の具体型が必要 | Raoh |
| 既存の Spring MVC プロジェクトへの最小限の追加 | Bean Validation |
| 多態的な構造を型安全に扱いたい | Raoh |

この2つは競合するものではない。同じアプリケーションの中で、シンプルなフォームには Bean Validation を、複雑な多態的入力には Raoh を使い分けることができる。

---

Part 2 のまとめとして: Bean Validation の問題は Bean Validation 自体にあるのではなく、**多態的な入力を「フラットなフォームクラス + 手続きValidator」という構造で表現しようとすること**にある。Raoh はその構造的ミスマッチを解消し、入力の形をそのままドメインの型として受け取れるようにする。

次のPart 3では、バリデーションを通過した後の世界——Always-Valid Layer——について詳しく見ていく。
