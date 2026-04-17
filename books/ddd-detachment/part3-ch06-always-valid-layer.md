---
title: "「ビジネスロジック」という曖昧な言葉を捨てる"
---

## 「ビジネスロジック」とは何か

Spring Boot + DDD の文脈で「ビジネスロジックはドメイン層に置く」という言葉をよく聞きます。しかしいざ実装しようとすると、「これはビジネスロジックか？」という疑問が生まれやすいです。

- 注文の合計金額を計算する → ビジネスロジック？
- 配送先住所のフォーマットを検証する → ビジネスロジック？
- データベースから注文を取得する → ビジネスロジックではない？
- 消費税率を掛ける → ビジネスロジック？

「ビジネスロジック」という言葉は、何が含まれるかを明確に定義しません。よく見られる説明は「UIや永続化に関係しない処理」という**否定形の定義**です。否定形で定義された概念は境界が曖昧で、同じコードを見て「これはビジネスロジックだ」「いや、インフラ層の責務だ」という議論が起きます。

## Always-Valid Layerという切り口

「ビジネスロジック」の代わりに使いたい概念が **Always-Valid Layer** です。

Always-Valid Layerとは、**そこに存在するデータは業務上常に正しい**という保証を持つ層のことです。言い換えると、この層に入ってくるデータは「検証済み」であり、null チェックや形式チェックを毎回やり直す必要がありません。

この定義は肯定形です。「何がある層か」が明確に言えます。

ミールス宅配サービスで考えてみましょう。以下のメソッドを見てください。

```java
// Always-Valid Layer の外側（バリデーション前）
public void createSubscription(String planType, String mealSetId, String frequency) {
    if (planType == null) throw new IllegalArgumentException("planType is required");
    if (!planType.matches("STANDARD|PREMIUM|CUSTOM")) throw new IllegalArgumentException("invalid planType");
    if (mealSetId == null || mealSetId.isBlank()) throw new IllegalArgumentException("mealSetId is required");
    // ... バリデーションが続く
    
    // やっと本来の処理
}
```

これが **Shotgun Parsing** アンチパターンです。呼ばれるたびに入力の正当性を確認するコードが散弾（shotgun）のように各所に散らばります。たとえば `mealSetId` が空でないことの確認だけで、Controller・Service・Repository の入口付近に同じガード節が何箇所も現れます。バリデーションルールが変わったとき、すべての箇所を探して修正しなければならず、漏れがあっても気づきにくいです。

対して Always-Valid Layer の中はこうなります。

```java
// Always-Valid Layer の内側（パース済み）
public String createSubscription(OrderPlan plan) {
    // plan は OrderPlan 型。型の不変条件が正当性を保証している。
    // ここでは plan が valid であることを前提に処理を書ける。
    return subscriptionRepository.save(plan);
}
```

`OrderPlan` は sealed interface であり、`StandardPlan | PremiumPlan | CustomPlan` のどれかです。それぞれのレコードのコンストラクタが不変条件を保証しているので、このメソッド内で再チェックする必要はありません。

## 境界はどこか

Always-Valid Layerの境界は「入力をパースしてドメインオブジェクトに変換する場所」です。

```text
HTTP リクエスト (JSON)
       ↓
  【境界: OrderPlanDecoder.decode()】
       ↓
  OrderPlan (Always-Valid Layer に入る)
       ↓
  SubscriptionService.create(OrderPlan)
       ↓
  SubscriptionRepository.save(OrderPlan)
```

境界の外側では `JsonNode` や `String` を扱います。境界を越えた瞬間から `OrderPlan` を扱います。以降の処理はすべて型の保証の上に立てます。

この構造は「バリデーションはどこでやるか」という議論に答えを与えます。**バリデーションは境界でやります。境界の内側では型が保証します。** バリデーションを「ドメイン層に置くかどうか」ではなく、「どこが境界か」として考えれば良いです。

## Always-Valid Layer と「ビジネスロジック」の違い

「ビジネスロジック」はロジックの**種類**で分類しようとします。Always-Valid Layerはデータの**状態**で分類します。

| | ビジネスロジック層 | Always-Valid Layer |
| --- | --- | --- |
| 定義 | UIや永続化に関係しない処理（否定形） | 常に正しいデータを扱う層（肯定形） |
| 境界の決め方 | 感覚・慣習に依存 | パースが完了する場所 |
| バリデーションの位置 | 議論が起きやすい | 境界（層の入口）で行います |
| 防御的プログラミング | 必要になりやすい | 不要（型が保証） |

Always-Valid Layerという概念を使うと、「このチェックはドメイン層に書くべきかサービス層に書くべきか」という議論が不要になります。**チェックは境界で一度だけ行い、型に変換したら以降は型を信頼します。**

---

次章では、この境界を「状態遷移」として表現することを見ていきます。ミールス宅配サービスの定期便管理（アクティブ/一時停止）は、Always-Valid Layerの考え方を状態ごとに適用した実例です。
