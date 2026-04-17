---
title: "未検証→検証済みという状態遷移"
---

## 状態を型で表す

前章でAlways-Valid Layerの概念を紹介しました。この章では、その概念を「状態遷移」という形でより具体的に適用します。

ミールス宅配サービスの定期便管理を例にとります。定期便には「アクティブ」と「一時停止」の2つの状態があります。

よくある実装はステータスフラグです。

```java
// よくある実装：フラグで状態を表す
public class Subscription {
    private String id;
    private String userId;
    private boolean suspended;         // true なら一時停止中
    private LocalDate nextDeliveryDate; // suspended=true のときは意味がない
    // ...
}
```

この実装には問題があります。`suspended = true` のとき `nextDeliveryDate` は意味を持ちませんが、フィールドとしては存在し続けます。「一時停止中なのに次回配送日が設定されている」という矛盾した状態が、構造上は可能になってしまいます。

sealed interface を使うと、状態ごとに「存在すべきフィールド」を正確に定義できます。

```java
public sealed interface Subscription
        permits Subscription.Active, Subscription.Suspended {

    UserId userId();
    OrderPlan plan();
    DeliveryFrequency frequency();

    // アクティブ: 次回配送日が必ず存在する
    record Active(
            SubscriptionId id,
            UserId userId,
            OrderPlan plan,
            DeliveryFrequency frequency,
            LocalDate nextDeliveryDate
    ) implements Subscription {}

    // 一時停止: 次回配送日は存在しない
    record Suspended(
            SubscriptionId id,
            UserId userId,
            OrderPlan plan,
            DeliveryFrequency frequency
    ) implements Subscription {}
}
```

`Active` には `nextDeliveryDate` があり、`Suspended` にはありません。「一時停止中なのに次回配送日がある」という状態は型として表現できません。コンパイラがそれを防ぎます。

### なぜ Subscription と OrderPlan の両方に frequency があるのか

目ざとい読者は気づいたかもしれませんが、`OrderPlan` も `frequency`（配送頻度）を持ち（4章で定義）、`Subscription` も `frequency` を持っています。同じ情報が二箇所にある——これは重複ではないか、という疑問が生じます。

本書の答えは「二つは別の意味を持つ」です。

- `OrderPlan.frequency` は**注文時点の希望**を表します。注文は発生した時点で記録として固定されるべきものであり、後から書き換えると監査上の問題になります。つまり `OrderPlan` は履歴としての不変データです。
- `Subscription.frequency` は**現在の運用値**を表します。ユーザーが「毎週から隔週に変更したい」と要望したとき、書き換わるのはこちらです。

この分離により、「注文時は毎週で申し込まれたが、今は隔週で運用されている」という食い違いを型として表現できます。仮に `changeFrequency()` という操作を追加するとしたら、そのシグネチャは `Subscription.Active → Subscription.Active` であり、戻り値の `frequency` だけが変わり `OrderPlan` 側はそのまま保持されます。

二つのモデルが別の概念（注文の意図 vs 運用の状態）を表している限り、同じフィールド名を持っていても重複ではありません。

## 振る舞いを型で制約する

状態遷移を表す振る舞いも、型で制約できます。

```java
public class SubscriptionBehavior {

    // 引数が Active 型であるため、
    // 「すでに一時停止中の定期便を一時停止する」はコンパイルエラーになる
    public Subscription.Suspended suspend(Subscription.Active active) {
        return new Subscription.Suspended(
                active.id(),
                active.userId(),
                active.plan(),
                active.frequency()
        );
    }

    // 引数が Suspended 型であるため、
    // 「すでにアクティブな定期便を再開する」はコンパイルエラーになる
    public Subscription.Active resume(Subscription.Suspended suspended,
                                      LocalDate nextDeliveryDate) {
        return new Subscription.Active(
                suspended.id(),
                suspended.userId(),
                suspended.plan(),
                suspended.frequency(),
                nextDeliveryDate
        );
    }
}
```

`suspend()` の引数は `Subscription.Active` です。`Subscription.Suspended` を渡そうとするとコンパイルエラーになります。「一時停止中の定期便をもう一度一時停止する」という業務上ありえない操作が、コンパイル時に排除されます。

メソッドの中に「すでに一時停止中かどうか」をチェックするコードは不要です。引数の型がそれを保証しています。

## Shotgun Parsing との対比

型で状態を表さない場合、各メソッドで防御的なチェックが必要になります。

```java
// 型を使わない場合：各メソッドでチェックが必要
public class SubscriptionService {

    public void suspend(String subscriptionId) {
        Subscription sub = repository.findById(subscriptionId);
        if (sub == null) throw new NotFoundException("定期便が見つかりません");
        if (sub.isSuspended()) throw new IllegalStateException("すでに一時停止中です");
        // やっと本来の処理
        sub.setSuspended(true);
        sub.setNextDeliveryDate(null);
        repository.save(sub);
    }

    public void resume(String subscriptionId, LocalDate nextDeliveryDate) {
        Subscription sub = repository.findById(subscriptionId);
        if (sub == null) throw new NotFoundException("定期便が見つかりません");
        if (!sub.isSuspended()) throw new IllegalStateException("アクティブな定期便です");
        if (nextDeliveryDate == null) throw new IllegalArgumentException("次回配送日は必須です");
        // やっと本来の処理
        sub.setSuspended(false);
        sub.setNextDeliveryDate(nextDeliveryDate);
        repository.save(sub);
    }
}
```

「一時停止中かどうか」のチェックが `suspend()` と `resume()` の両方に散らばっています。チェックの漏れや不整合が生じ、チェック自体がコードの本来の意図を埋もれさせます。

sealed interface + 状態ごとの record を使えば、こうした防御的コードは不要になります。

## "Make Illegal States Unrepresentable"

この設計原則を **"Make Illegal States Unrepresentable"** といいます。不正な状態を型として表現できないようにする、という意味です。

- 「一時停止中なのに次回配送日がある」→ `Suspended` には `nextDeliveryDate` フィールドがないので表現できません
- 「一時停止中の定期便を一時停止する」→ `suspend()` の引数が `Active` 型なので渡せません
- 「アクティブな定期便を再開する」→ `resume()` の引数が `Suspended` 型なので渡せません

Java の sealed interface と record を組み合わせることで、この原則を実現できます。

---

次章では、こうして設計されたドメインモデルが「ただの record」で十分である理由を説明します。バリデーションも永続化の知識も持たない、純粋なデータ構造としての record の価値を示します。
