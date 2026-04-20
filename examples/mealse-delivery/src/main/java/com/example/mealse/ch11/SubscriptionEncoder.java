package com.example.mealse.ch11;

import com.example.mealse.domain.MealId;
import com.example.mealse.domain.OrderPlan;
import com.example.mealse.domain.Subscription;
import net.unit8.raoh.encode.Encoder;

import java.util.Map;

import static net.unit8.raoh.encode.MapEncoders.nested;
import static net.unit8.raoh.encode.MapEncoders.object;
import static net.unit8.raoh.encode.MapEncoders.property;
import static net.unit8.raoh.encode.ObjectEncoders.bool;
import static net.unit8.raoh.encode.ObjectEncoders.date;
import static net.unit8.raoh.encode.ObjectEncoders.string;

/**
 * Encoders that convert {@link Subscription} domain models into an external
 * representation ({@code Map<String, Object>}) suitable for an HTTP JSON response.
 *
 * <p>This is the dual of {@link com.example.mealse.ch04.OrderPlanDecoder}:
 * where the decoder converts an incoming {@code JsonNode} into a domain type,
 * the encoder converts a domain type back into an external representation.</p>
 *
 * <p>The sealed-interface dispatch ({@link Subscription.Active} vs
 * {@link Subscription.Suspended}, and the {@code OrderPlan} variants) is handled
 * with {@code switch} expressions. Each concrete variant has its own {@link Encoder}
 * defined with {@code object(property(...), ...)}, mirroring the shape of the
 * corresponding decoder.</p>
 */
public final class SubscriptionEncoder {

    private SubscriptionEncoder() {}

    // ---- OrderPlan encoders (one per sealed variant) -----------------------

    static final Encoder<OrderPlan.StandardPlan, Map<String, Object>> STANDARD_PLAN_ENCODER = object(
            property("planType",  p -> "STANDARD",                  string()),
            property("mealSetId", p -> p.mealSetId().value(),       string()),
            property("frequency", p -> p.frequency().name(),        string())
    );

    static final Encoder<OrderPlan.PremiumPlan, Map<String, Object>> PREMIUM_PLAN_ENCODER = object(
            property("planType",      p -> "PREMIUM",                string()),
            property("mealSetId",     p -> p.mealSetId().value(),    string()),
            property("frequency",     p -> p.frequency().name(),     string()),
            property("includeFrozen", OrderPlan.PremiumPlan::includeFrozen, bool())
    );

    static final Encoder<OrderPlan.CustomPlan, Map<String, Object>> CUSTOM_PLAN_ENCODER = object(
            property("planType",  p -> "CUSTOM",                                                   string()),
            property("meals",     p -> p.meals().stream().map(MealId::value).toList(),             v -> v),
            property("frequency", p -> p.frequency().name(),                                       string()),
            property("startDate", OrderPlan.CustomPlan::startDate,                                 date())
    );

    /**
     * Encoder that dispatches on {@link OrderPlan} variants.
     */
    public static final Encoder<OrderPlan, Map<String, Object>> ORDER_PLAN_ENCODER = plan ->
            switch (plan) {
                case OrderPlan.StandardPlan p -> STANDARD_PLAN_ENCODER.encode(p);
                case OrderPlan.PremiumPlan  p -> PREMIUM_PLAN_ENCODER.encode(p);
                case OrderPlan.CustomPlan   p -> CUSTOM_PLAN_ENCODER.encode(p);
            };

    // ---- Subscription encoders (one per sealed variant) ---------------------

    static final Encoder<Subscription.Active, Map<String, Object>> ACTIVE_ENCODER = object(
            property("id",               s -> s.id().value(),                 string()),
            property("userId",           s -> s.userId().value(),             string()),
            property("status",           s -> "ACTIVE",                       string()),
            property("plan",             Subscription.Active::plan,           nested(ORDER_PLAN_ENCODER)),
            property("frequency",        s -> s.frequency().name(),           string()),
            property("nextDeliveryDate", Subscription.Active::nextDeliveryDate, date())
    );

    static final Encoder<Subscription.Suspended, Map<String, Object>> SUSPENDED_ENCODER = object(
            property("id",        s -> s.id().value(),                    string()),
            property("userId",    s -> s.userId().value(),                string()),
            property("status",    s -> "SUSPENDED",                       string()),
            property("plan",      Subscription.Suspended::plan,           nested(ORDER_PLAN_ENCODER)),
            property("frequency", s -> s.frequency().name(),              string())
    );

    /**
     * Encoder that dispatches on {@link Subscription} variants.
     *
     * <p>The output shape differs between {@code Active} (includes
     * {@code nextDeliveryDate}) and {@code Suspended} (no such field), matching the
     * sealed-interface structure of the domain.</p>
     */
    public static final Encoder<Subscription, Map<String, Object>> SUBSCRIPTION_ENCODER = subscription ->
            switch (subscription) {
                case Subscription.Active    a -> ACTIVE_ENCODER.encode(a);
                case Subscription.Suspended s -> SUSPENDED_ENCODER.encode(s);
            };
}
