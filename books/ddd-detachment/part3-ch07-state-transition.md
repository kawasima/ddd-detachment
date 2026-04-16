---
title: "未検証→検証済みという状態遷移"
---

## 状態を型で表す

前章でAlways-Valid Layerの概念を紹介した。この章では、その概念を「状態遷移」という形でより具体的に適用する。

ミールス宅配サービスの定期便管理を例にとる。定期便には「アクティブ」と「一時停止」の2つの状態がある。

よくある実装はステータスフラグだ。

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

この実装には問題がある。`suspended = true` のとき `nextDeliveryDate` は意味を持たないが、フィールドとしては存在し続ける。「一時停止中なのに次回配送日が設定されている」という矛盾した状態が、構造上は可能になってしまう。

sealed interface を使うと、状態ごとに「存在すべきフィールド」を正確に定義できる。

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

`Active` には `nextDeliveryDate` があり、`Suspended` にはない。「一時停止中なのに次回配送日がある」という状態は型として表現できない。コンパイラがそれを防ぐ。

## 振る舞いを型で制約する

状態遷移を表す振る舞いも、型で制約できる。

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

`suspend()` の引数は `Subscription.Active` だ。`Subscription.Suspended` を渡そうとするとコンパイルエラーになる。「一時停止中の定期便をもう一度一時停止する」という業務上ありえない操作が、コンパイル時に排除される。

メソッドの中に「すでに一時停止中かどうか」をチェックするコードは不要だ。引数の型がそれを保証している。

## Shotgun Parsing との対比

型で状態を表さない場合、各メソッドで防御的なチェックが必要になる。

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

「一時停止中かどうか」のチェックが `suspend()` と `resume()` の両方に散らばっている。チェックの漏れや不整合が起きやすく、チェック自体がコードの本来の意図を埋もれさせる。

sealed interface + 状態ごとの record を使えば、こうした防御的コードは不要になる。

## "Make Illegal States Unrepresentable"

この設計原則を **"Make Illegal States Unrepresentable"** という。不正な状態を型として表現できないようにする、という意味だ。

- 「一時停止中なのに次回配送日がある」→ `Suspended` には `nextDeliveryDate` フィールドがないので表現できない
- 「一時停止中の定期便を一時停止する」→ `suspend()` の引数が `Active` 型なので渡せない
- 「アクティブな定期便を再開する」→ `resume()` の引数が `Suspended` 型なので渡せない

Java の sealed interface と record を組み合わせることで、この原則を実現できる。

---

次章では、こうして設計されたドメインモデルが「ただの record」で十分である理由を説明する。バリデーションも永続化の知識も持たない、純粋なデータ構造としての record の価値を見ていく。
