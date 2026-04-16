package com.example.mealse.ch11;

import com.example.mealse.domain.DeliveryFrequency;
import com.example.mealse.domain.OrderPlan;
import com.example.mealse.domain.Subscription;
import com.example.mealse.domain.SubscriptionId;
import com.example.mealse.domain.UserId;
import com.example.mealse.domain.MealSetId;
import com.example.mealse.domain.SubscriptionBehavior;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that demonstrate the full round-trip through the repository:
 * domain model → persistence row → domain model.
 *
 * <p>These tests verify that the mapping logic in {@link InMemorySubscriptionRepository}
 * correctly preserves domain state across save/load cycles.</p>
 */
class OrderControllerFlowTest {

    private final InMemorySubscriptionRepository repository = new InMemorySubscriptionRepository();
    private final SubscriptionBehavior behavior = new SubscriptionBehavior();

    @Test
    void saveAndReload_activeStandardSubscription() {
        Subscription.Active active = new Subscription.Active(
                new SubscriptionId("sub-001"),
                new UserId("user-001"),
                new OrderPlan.StandardPlan(new MealSetId("set-001"), DeliveryFrequency.WEEKLY),
                DeliveryFrequency.WEEKLY,
                LocalDate.now().plusDays(7)
        );

        repository.save(active);

        Subscription.Active loaded = repository.findActive(new SubscriptionId("sub-001"))
                .orElseThrow();
        assertThat(loaded.userId()).isEqualTo(active.userId());
        assertThat(loaded.plan()).isEqualTo(active.plan());
        assertThat(loaded.nextDeliveryDate()).isEqualTo(active.nextDeliveryDate());
    }

    @Test
    void suspend_changesStatusInRepository() {
        Subscription.Active active = new Subscription.Active(
                new SubscriptionId("sub-002"),
                new UserId("user-002"),
                new OrderPlan.PremiumPlan(new MealSetId("set-002"), DeliveryFrequency.BIWEEKLY, true),
                DeliveryFrequency.BIWEEKLY,
                LocalDate.now().plusDays(14)
        );
        repository.save(active);

        Subscription.Suspended suspended = behavior.suspend(active);
        repository.save(suspended);

        assertThat(repository.findActive(new SubscriptionId("sub-002"))).isEmpty();
        Subscription.Suspended loaded = repository.findSuspended(new SubscriptionId("sub-002"))
                .orElseThrow();
        assertThat(loaded.userId()).isEqualTo(active.userId());
        assertThat(loaded.plan()).isEqualTo(active.plan());
    }

    @Test
    void resume_changesStatusBackToActive() {
        Subscription.Active active = new Subscription.Active(
                new SubscriptionId("sub-003"),
                new UserId("user-003"),
                new OrderPlan.StandardPlan(new MealSetId("set-003"), DeliveryFrequency.WEEKLY),
                DeliveryFrequency.WEEKLY,
                LocalDate.now().plusDays(7)
        );
        repository.save(active);
        Subscription.Suspended suspended = behavior.suspend(active);
        repository.save(suspended);

        LocalDate resumeDate = LocalDate.now().plusDays(10);
        Subscription.Active resumed = behavior.resume(suspended, resumeDate);
        repository.save(resumed);

        assertThat(repository.findSuspended(new SubscriptionId("sub-003"))).isEmpty();
        Subscription.Active loaded = repository.findActive(new SubscriptionId("sub-003"))
                .orElseThrow();
        assertThat(loaded.nextDeliveryDate()).isEqualTo(resumeDate);
    }
}
