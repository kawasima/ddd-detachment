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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that demonstrate the full round-trip through the repository and encoder:
 * domain model → persistence row → domain model → encoded response.
 *
 * <p>These tests verify that the mapping logic in {@link InMemorySubscriptionRepository}
 * correctly preserves domain state across save/load cycles, and that
 * {@link SubscriptionEncoder} produces the expected response shape.</p>
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

    @Test
    void findById_returnsActiveVariant() {
        Subscription.Active active = new Subscription.Active(
                new SubscriptionId("sub-004"),
                new UserId("user-004"),
                new OrderPlan.StandardPlan(new MealSetId("set-004"), DeliveryFrequency.WEEKLY),
                DeliveryFrequency.WEEKLY,
                LocalDate.now().plusDays(7)
        );
        repository.save(active);

        Subscription loaded = repository.findById(new SubscriptionId("sub-004"))
                .orElseThrow();
        assertThat(loaded).isInstanceOf(Subscription.Active.class);
    }

    @Test
    void encoder_producesExpectedShapeForActiveStandard() {
        LocalDate deliveryDate = LocalDate.of(2026, 5, 1);
        Subscription.Active active = new Subscription.Active(
                new SubscriptionId("sub-100"),
                new UserId("user-100"),
                new OrderPlan.StandardPlan(new MealSetId("set-100"), DeliveryFrequency.WEEKLY),
                DeliveryFrequency.WEEKLY,
                deliveryDate
        );

        Map<String, Object> encoded = SubscriptionEncoder.SUBSCRIPTION_ENCODER.encode(active);

        assertThat(encoded).containsEntry("id", "sub-100");
        assertThat(encoded).containsEntry("userId", "user-100");
        assertThat(encoded).containsEntry("status", "ACTIVE");
        assertThat(encoded).containsEntry("frequency", "WEEKLY");
        assertThat(encoded).containsEntry("nextDeliveryDate", deliveryDate.toString());
        assertThat(encoded.get("plan")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) encoded.get("plan");
        assertThat(plan).containsEntry("planType", "STANDARD");
        assertThat(plan).containsEntry("mealSetId", "set-100");
        assertThat(plan).containsEntry("frequency", "WEEKLY");
    }

    @Test
    void encoder_producesExpectedShapeForSuspendedPremium() {
        Subscription.Suspended suspended = new Subscription.Suspended(
                new SubscriptionId("sub-101"),
                new UserId("user-101"),
                new OrderPlan.PremiumPlan(new MealSetId("set-101"), DeliveryFrequency.BIWEEKLY, true),
                DeliveryFrequency.BIWEEKLY
        );

        Map<String, Object> encoded = SubscriptionEncoder.SUBSCRIPTION_ENCODER.encode(suspended);

        assertThat(encoded).containsEntry("status", "SUSPENDED");
        assertThat(encoded).doesNotContainKey("nextDeliveryDate");
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) encoded.get("plan");
        assertThat(plan).containsEntry("planType", "PREMIUM");
        assertThat(plan).containsEntry("includeFrozen", true);
    }

    @Test
    void encoder_producesExpectedShapeForCustomPlan() {
        LocalDate startDate = LocalDate.of(2026, 6, 1);
        Subscription.Active active = new Subscription.Active(
                new SubscriptionId("sub-102"),
                new UserId("user-102"),
                new OrderPlan.CustomPlan(
                        java.util.List.of(
                                new com.example.mealse.domain.MealId("m-1"),
                                new com.example.mealse.domain.MealId("m-2")),
                        DeliveryFrequency.WEEKLY,
                        startDate),
                DeliveryFrequency.WEEKLY,
                startDate.plusDays(7)
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) SubscriptionEncoder.SUBSCRIPTION_ENCODER
                .encode(active).get("plan");

        assertThat(plan).containsEntry("planType", "CUSTOM");
        assertThat(plan).containsEntry("startDate", startDate.toString());
        @SuppressWarnings("unchecked")
        java.util.List<Object> meals = (java.util.List<Object>) plan.get("meals");
        assertThat(meals).containsExactly("m-1", "m-2");
    }
}
