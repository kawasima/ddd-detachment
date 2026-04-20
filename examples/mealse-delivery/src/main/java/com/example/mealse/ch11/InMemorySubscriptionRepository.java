package com.example.mealse.ch11;

import com.example.mealse.domain.DeliveryFrequency;
import com.example.mealse.domain.MealId;
import com.example.mealse.domain.MealSetId;
import com.example.mealse.domain.OrderPlan;
import com.example.mealse.domain.Subscription;
import com.example.mealse.domain.SubscriptionId;
import com.example.mealse.domain.UserId;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory implementation of {@link SubscriptionRepository}.
 *
 * <p>This class stands in for a real database repository (e.g., one backed by jOOQ)
 * to demonstrate the key architectural concept: <em>the mapping between domain model
 * and persistence representation lives here, not in the domain model itself</em>.</p>
 *
 * <p>In a real application, this class would use jOOQ's DSL to issue SQL:</p>
 * <pre>{@code
 * switch (subscription) {
 *     case Subscription.Active a -> jooq.insertInto(SUBSCRIPTIONS)
 *             .set(SUBSCRIPTIONS.STATUS, "ACTIVE")
 *             .set(SUBSCRIPTIONS.NEXT_DELIVERY_DATE, a.nextDeliveryDate())
 *             ...
 *             .execute();
 *     case Subscription.Suspended s -> ...
 * }
 * }</pre>
 */
@Repository
public class InMemorySubscriptionRepository implements SubscriptionRepository {

    /**
     * Row structure mirroring the subscriptions table columns.
     *
     * @param id the subscription ID
     * @param userId the user ID
     * @param status the status string ("ACTIVE" or "SUSPENDED")
     * @param planType the plan type string
     * @param mealSetId the meal set ID (for STANDARD/PREMIUM plans)
     * @param frequency the delivery frequency string
     * @param includeFrozen whether frozen meals are included (for PREMIUM plan)
     * @param mealIds comma-separated meal IDs (for CUSTOM plan)
     * @param startDate the start date (for CUSTOM plan)
     * @param nextDeliveryDate the next delivery date (for ACTIVE subscriptions)
     */
    record SubscriptionRow(
            String id,
            String userId,
            String status,
            String planType,
            String mealSetId,
            String frequency,
            boolean includeFrozen,
            String mealIds,
            LocalDate startDate,
            LocalDate nextDeliveryDate
    ) {}

    private final Map<String, SubscriptionRow> store = new HashMap<>();

    /**
     * {@inheritDoc}
     *
     * <p>This method is where the domain-to-persistence mapping happens.
     * The domain model ({@link Subscription}) knows nothing about rows or columns;
     * this repository implementation handles the translation.</p>
     */
    @Override
    public void save(Subscription subscription) {
        SubscriptionRow row = switch (subscription) {
            case Subscription.Active a -> toActiveRow(a);
            case Subscription.Suspended s -> toSuspendedRow(s);
        };
        store.put(row.id(), row);
    }

    @Override
    public Optional<Subscription> findById(SubscriptionId id) {
        SubscriptionRow row = store.get(id.value());
        if (row == null) {
            return Optional.empty();
        }
        return switch (row.status()) {
            case "ACTIVE"    -> Optional.of(toActive(row));
            case "SUSPENDED" -> Optional.of(toSuspended(row));
            default -> throw new IllegalStateException("Unknown status: " + row.status());
        };
    }

    @Override
    public Optional<Subscription.Active> findActive(SubscriptionId id) {
        SubscriptionRow row = store.get(id.value());
        if (row == null || !"ACTIVE".equals(row.status())) {
            return Optional.empty();
        }
        return Optional.of(toActive(row));
    }

    @Override
    public Optional<Subscription.Suspended> findSuspended(SubscriptionId id) {
        SubscriptionRow row = store.get(id.value());
        if (row == null || !"SUSPENDED".equals(row.status())) {
            return Optional.empty();
        }
        return Optional.of(toSuspended(row));
    }

    // ---- domain → row mapping ------------------------------------------------

    private SubscriptionRow toActiveRow(Subscription.Active a) {
        return buildRow(a.id().value(), a.userId().value(), "ACTIVE",
                a.plan(), a.frequency(), a.nextDeliveryDate());
    }

    private SubscriptionRow toSuspendedRow(Subscription.Suspended s) {
        return buildRow(s.id().value(), s.userId().value(), "SUSPENDED",
                s.plan(), s.frequency(), null);
    }

    private SubscriptionRow buildRow(String id, String userId, String status,
                                     OrderPlan plan, DeliveryFrequency frequency,
                                     LocalDate nextDeliveryDate) {
        return switch (plan) {
            case OrderPlan.StandardPlan p -> new SubscriptionRow(
                    id, userId, status,
                    "STANDARD", p.mealSetId().value(), frequency.name(),
                    false, null, null, nextDeliveryDate);
            case OrderPlan.PremiumPlan p -> new SubscriptionRow(
                    id, userId, status,
                    "PREMIUM", p.mealSetId().value(), frequency.name(),
                    p.includeFrozen(), null, null, nextDeliveryDate);
            case OrderPlan.CustomPlan p -> new SubscriptionRow(
                    id, userId, status,
                    "CUSTOM", null, frequency.name(),
                    false,
                    String.join(",", p.meals().stream().map(MealId::value).toList()),
                    p.startDate(), nextDeliveryDate);
        };
    }

    // ---- row → domain mapping ------------------------------------------------

    private Subscription.Active toActive(SubscriptionRow row) {
        return new Subscription.Active(
                new SubscriptionId(row.id()),
                new UserId(row.userId()),
                toPlan(row),
                DeliveryFrequency.valueOf(row.frequency()),
                row.nextDeliveryDate()
        );
    }

    private Subscription.Suspended toSuspended(SubscriptionRow row) {
        return new Subscription.Suspended(
                new SubscriptionId(row.id()),
                new UserId(row.userId()),
                toPlan(row),
                DeliveryFrequency.valueOf(row.frequency())
        );
    }

    private OrderPlan toPlan(SubscriptionRow row) {
        return switch (row.planType()) {
            case "STANDARD" -> new OrderPlan.StandardPlan(
                    new MealSetId(row.mealSetId()),
                    DeliveryFrequency.valueOf(row.frequency()));
            case "PREMIUM" -> new OrderPlan.PremiumPlan(
                    new MealSetId(row.mealSetId()),
                    DeliveryFrequency.valueOf(row.frequency()),
                    row.includeFrozen());
            case "CUSTOM" -> new OrderPlan.CustomPlan(
                    List.of(row.mealIds().split(",")).stream()
                            .map(MealId::new).toList(),
                    DeliveryFrequency.valueOf(row.frequency()),
                    row.startDate());
            default -> throw new IllegalStateException("Unknown planType: " + row.planType());
        };
    }
}
