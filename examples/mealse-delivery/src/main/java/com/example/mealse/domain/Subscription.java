package com.example.mealse.domain;

import java.time.LocalDate;

public sealed interface Subscription
        permits Subscription.Active, Subscription.Suspended {

    UserId userId();
    OrderPlan plan();
    DeliveryFrequency frequency();

    record Active(
            SubscriptionId id,
            UserId userId,
            OrderPlan plan,
            DeliveryFrequency frequency,
            LocalDate nextDeliveryDate
    ) implements Subscription {}

    record Suspended(
            SubscriptionId id,
            UserId userId,
            OrderPlan plan,
            DeliveryFrequency frequency
    ) implements Subscription {}
}
