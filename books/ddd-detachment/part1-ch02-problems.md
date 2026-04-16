---
title: "その構成の何が問題か"
---

## 問題を整理する

前章で示した典型的な Spring Boot + DDD 構成には、3つの問題がある。それぞれ独立した問題ではなく、根が同じだ。

## 問題1: バリデーション通過後も型が確定しない

Bean Validation でバリデーションを通過した後も、変数の型は変わらない。

```java
// バリデーション通過後の状態
OrderPlanForm form; // planType = "STANDARD"
```

`planType = "STANDARD"` のとき、`mealSetId` は必ず存在し `mealIds` は null のはずだ。しかしそれは **バリデーションロジックの中だけで保証された知識** であり、`OrderPlanForm` という型には反映されていない。

その結果、`form` を受け取ったコードはすべて、どのプランなのかをもう一度確認しなければならない。

```java
// バリデーション通過後でも、各所で再チェックが必要
private OrderPlan buildPlan(CreateOrderCommand command) {
    return switch (command.getPlanType()) { // 文字列で再分岐
        case "STANDARD" -> ...;
        case "PREMIUM"  -> ...;
        case "CUSTOM"   -> ...;
        default -> throw new IllegalArgumentException(...); // 実行時エラー
    };
}
```

コンパイラはこの分岐が網羅的かどうかを確認できない。`"CUSTOM"` のスペルミスも、`"PREMIUM"` の処理の漏れも、実行時にしか発覚しない。

## 問題2: 詰め替えがコードの大半を占める

HTTP リクエストから DB 書き込みまでに、少なくとも3回の詰め替えが発生する。

```text
OrderPlanForm
    ↓ 詰め替え（Controller）
CreateOrderCommand
    ↓ 詰め替え（ApplicationService）
Subscription + OrderPlan（ドメインモデル）
    ↓ 詰め替え（JPA が自動マッピング）
DB
```

各詰め替えのコードは、それ自体はシンプルな値コピーだ。しかし積み重なると、「何をやっているかわかるコード」の大半が「値をコピーするだけのコード」になる。

さらに、詰め替えは変更の波及点になる。プランに新しいフィールドが加わると、`OrderPlanForm`、`CreateOrderCommand`、`OrderPlan` のすべてを修正し、それぞれの詰め替えコードも修正しなければならない。修正箇所が多いと、どこかで漏れが発生しやすい。

## 問題3: ドメインモデルが複数の関心を背負う

`Subscription` クラスを見ると、1つのクラスが複数の関心を背負っている。

```java
@Entity                       // 永続化の関心
@Table(name = "subscriptions")
public class Subscription {

    @NotNull                  // バリデーションの関心
    private String userId;

    @Enumerated(EnumType.STRING)
    private SubscriptionStatus status;

    private LocalDate nextDeliveryDate;

    public void suspend() {
        if (status == SUSPENDED) {   // 状態管理の関心
            throw new IllegalStateException(...);
        }
        this.status = SUSPENDED;
        this.nextDeliveryDate = null;
    }
}
```

このクラスを変更する理由が複数ある。

- テーブル構造が変わった → `@Column` を修正
- バリデーションルールが変わった → `@NotNull` などを修正
- 状態遷移の仕様が変わった → `suspend()` の中を修正

複数の理由で変更されるクラスは、変更が衝突しやすい。また、このクラスを単体テストしようとすると Spring Context や JPA の設定が必要になることがある。

## 3つの問題に共通する根

これらの問題は、それぞれ独立した課題に見えるが、根は同じだ。

**バリデーション・型変換・永続化の知識が、あるべき場所に置かれていない。**

- バリデーションの知識がドメインモデル（`@NotNull`）に混入している
- 永続化の知識がドメインモデル（`@Entity`）に混入している
- 型を確定させる知識がバリデーションクラスの中に閉じ込められ、外には漏れ出ない

各知識を適切な場所に置き直すと、3つの問題は同時に解消される。

| 問題 | 根本原因 | 解決策（本書のアプローチ） |
| --- | --- | --- |
| 型が確定しない | バリデーションと型変換が分離している | デコーダが型変換とバリデーションを同時に行う |
| 詰め替えが多い | 各レイヤーで同じデータを別の型で持ち続ける | 境界で一度変換し、ドメインモデルをそのまま受け渡す |
| ドメインモデルが重い | バリデーション・永続化の知識がドメインモデルに混入 | 各知識をデコーダ・リポジトリに分散させる |

---

Part 2 からは、この解決策を具体的に見ていく。まず「バリデーションと型変換の同時処理」から始める。
