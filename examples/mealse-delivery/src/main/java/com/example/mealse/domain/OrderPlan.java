package com.example.mealse.domain;

import java.time.LocalDate;
import java.util.List;

public sealed interface OrderPlan
        permits OrderPlan.StandardPlan, OrderPlan.PremiumPlan, OrderPlan.CustomPlan {

    record StandardPlan(
            MealSetId mealSetId,
            DeliveryFrequency frequency
    ) implements OrderPlan {}

    record PremiumPlan(
            MealSetId mealSetId,
            DeliveryFrequency frequency,
            boolean includeFrozen
    ) implements OrderPlan {}

    record CustomPlan(
            List<MealId> meals,
            DeliveryFrequency frequency,
            LocalDate startDate
    ) implements OrderPlan {}
}
