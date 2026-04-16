package com.example.mealse.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionBehaviorTest {

    private final SubscriptionBehavior behavior = new SubscriptionBehavior();

    private Subscription.Active activeSubscription() {
        return new Subscription.Active(
                new SubscriptionId("sub-001"),
                new UserId("user-001"),
                new OrderPlan.StandardPlan(
                        new MealSetId("set-001"),
                        DeliveryFrequency.WEEKLY
                ),
                DeliveryFrequency.WEEKLY,
                LocalDate.now().plusDays(7)
        );
    }

    @Test
    void suspend_returnsSupended() {
        Subscription.Active active = activeSubscription();

        Subscription.Suspended suspended = behavior.suspend(active);

        assertThat(suspended.id()).isEqualTo(active.id());
        assertThat(suspended.userId()).isEqualTo(active.userId());
        assertThat(suspended.plan()).isEqualTo(active.plan());
    }

    @Test
    void resume_returnsActive() {
        Subscription.Active active = activeSubscription();
        Subscription.Suspended suspended = behavior.suspend(active);
        LocalDate nextDelivery = LocalDate.now().plusDays(14);

        Subscription.Active resumed = behavior.resume(suspended, nextDelivery);

        assertThat(resumed.id()).isEqualTo(suspended.id());
        assertThat(resumed.nextDeliveryDate()).isEqualTo(nextDelivery);
    }

    @Test
    void suspend_then_resume_roundtrip() {
        Subscription.Active original = activeSubscription();

        Subscription.Suspended suspended = behavior.suspend(original);
        Subscription.Active resumed = behavior.resume(suspended, LocalDate.now().plusDays(7));

        assertThat(resumed.userId()).isEqualTo(original.userId());
        assertThat(resumed.plan()).isEqualTo(original.plan());
        assertThat(resumed.frequency()).isEqualTo(original.frequency());
    }
}
