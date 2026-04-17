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
| I/O を伴う検証（DB 存在確認など） | `ConstraintValidator` 内で自前実装（Spring 管理が必要） | `flatMap(gateway::findById)` でデコーダに組み込める |

なお Bean Validation でも `@GroupSequenceProvider` や Jackson の `@JsonSubTypes` を活用すれば多態的入力に近い表現が可能です。これらの拡張の範囲とトレードオフについては3章末尾の「Bean Validation の拡張メカニズム」「Jackson の polymorphic deserialization という選択肢」を参照してください。本章は「本書の基本構成である Bean Validation」と「本書が推す Raoh デコーダ」の二者を同じ土俵で並べる比較として読んでください。

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

### Bean Validation で「対応漏れ」が起きるとき

Bean Validation のアプローチで `TrialPlan` を追加した場合、何が起きるかをコードで確認します。

バリデーターの `switch` に `"TRIAL"` の `case` を追加し忘れた場合、`default -> true` がそのリクエストを素通りさせます。

```java
// バリデーター（TrialPlan の case を追加し忘れた）
return switch (form.getPlanType()) {
    case "STANDARD" -> validateStandard(form, context);
    case "PREMIUM"  -> validatePremium(form, context);
    case "CUSTOM"   -> validateCustom(form, context);
    default -> true;  // ← "TRIAL" が来ても検証なしで通過してしまう
};
```

バリデーターをすり抜けた `"TRIAL"` は、コントローラーの `switch` に到達します。こちらに `case "TRIAL"` を追加していれば問題ありませんが、両方を追加し忘れていると次のような流れになります。

```java
// コントローラー（こちらも TrialPlan の case を追加し忘れた）
OrderPlan plan = switch (form.getPlanType()) {
    case "STANDARD" -> new StandardPlan(...);
    case "PREMIUM"  -> new PremiumPlan(...);
    case "CUSTOM"   -> new CustomPlan(...);
    default -> throw new IllegalStateException("unreachable");
    // ↑ バリデーターが true を返したので "TRIAL" がここに到達し、
    //   本番環境で初めて IllegalStateException が投げられる
};
```

この2つの `switch` はどちらも文法的に正しいJavaコードです。バリデーターが `true` を返すのも、コントローラーが例外を投げるのも、どちらも「コードとして問題がない」状態です。**コンパイラはこの対応漏れを検出できません。** `TrialPlan` のリクエストが届くまで、このバグはテストをくぐり抜けて本番環境に潜伏します。バリデーターを追加した開発者とコントローラーを修正した開発者が別であれば、なおさら気づきにくくなります。

Raoh では、`sealed interface OrderPlan` に `permits TrialPlan` を追加した瞬間、`switch (plan)` を書いているすべての箇所が `case TrialPlan` を要求するコンパイルエラーを出します。「新しいプランを追加するとき、対応が必要な場所をコンパイラが教えてくれる」——この違いは、プランの種類が増えるほど大きくなります。

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

本章のまとめ: Bean Validation の問題は Bean Validation 自体にあるのではなく、**多態的な入力を「フラットなフォームクラス + 手続きValidator」という構造で表現しようとすること**にあります。Raoh はその構造的ミスマッチを解消し、入力の形をそのままドメインの型として受け取れるようにします。

次の6〜8章では、バリデーションを通過した後の世界——Always-Valid Layer——を詳しく扱います。
