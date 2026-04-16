package com.example.mealse.domain;

/**
 * 定期便の状態遷移を表す振る舞い。
 *
 * <p>Always-Valid Layer の内側で動作するため、引数はすべて正当な状態であることが保証されている。
 * メソッド内でのnullチェックや状態チェックは不要。</p>
 */
public class SubscriptionBehavior {

    /**
     * アクティブな定期便を一時停止する。
     *
     * <p>引数の型が {@link Subscription.Active} であるため、
     * 「すでに一時停止している定期便を一時停止しようとする」ケースはコンパイル時に排除される。</p>
     *
     * @param active 一時停止対象のアクティブな定期便
     * @return 一時停止状態の定期便
     */
    public Subscription.Suspended suspend(Subscription.Active active) {
        return new Subscription.Suspended(
                active.id(),
                active.userId(),
                active.plan(),
                active.frequency()
        );
    }

    /**
     * 一時停止中の定期便を再開する。
     *
     * <p>引数の型が {@link Subscription.Suspended} であるため、
     * 「すでにアクティブな定期便を再開しようとする」ケースはコンパイル時に排除される。</p>
     *
     * @param suspended 再開対象の一時停止中の定期便
     * @param nextDeliveryDate 次回配送日
     * @return アクティブな定期便
     */
    public Subscription.Active resume(Subscription.Suspended suspended, java.time.LocalDate nextDeliveryDate) {
        return new Subscription.Active(
                suspended.id(),
                suspended.userId(),
                suspended.plan(),
                suspended.frequency(),
                nextDeliveryDate
        );
    }
}
