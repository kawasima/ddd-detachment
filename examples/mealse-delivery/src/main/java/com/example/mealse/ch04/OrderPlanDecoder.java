package com.example.mealse.ch04;

import com.example.mealse.domain.DeliveryFrequency;
import com.example.mealse.domain.MealId;
import com.example.mealse.domain.MealSetId;
import com.example.mealse.domain.OrderPlan;
import net.unit8.raoh.Result;
import net.unit8.raoh.decode.Decoder;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.Map;

import static net.unit8.raoh.json.JsonDecoders.*;

public class OrderPlanDecoder {

    static final Decoder<JsonNode, OrderPlan.StandardPlan> STANDARD_PLAN_DECODER = combine(
            field("mealSetId", string().minLength(1)).map(MealSetId::new),
            field("frequency", enumOf(DeliveryFrequency.class))
    ).map(OrderPlan.StandardPlan::new);

    static final Decoder<JsonNode, OrderPlan.PremiumPlan> PREMIUM_PLAN_DECODER = combine(
            field("mealSetId", string().minLength(1)).map(MealSetId::new),
            field("frequency", enumOf(DeliveryFrequency.class)),
            field("includeFrozen", bool())
    ).map(OrderPlan.PremiumPlan::new);

    static final Decoder<JsonNode, LocalDate> START_DATE_DECODER = (in, path) -> {
        Result<LocalDate> result = field("startDate", string().date()).decode(in, path);
        return result.flatMap(d ->
                d.isBefore(LocalDate.now().plusDays(3))
                        ? Result.fail(path.append("startDate"), "startDate.tooSoon", "開始日は3日以上先の日付を指定してください")
                        : Result.ok(d));
    };

    static final Decoder<JsonNode, OrderPlan.CustomPlan> CUSTOM_PLAN_DECODER = combine(
            field("meals", list(string().minLength(1)).minSize(1))
                    .map(ids -> ids.stream().map(MealId::new).toList()),
            field("frequency", enumOf(DeliveryFrequency.class)),
            START_DATE_DECODER
    ).map(OrderPlan.CustomPlan::new);

    public static final Decoder<JsonNode, OrderPlan> ORDER_PLAN_DECODER =
            discriminate("planType", Map.of(
                    "STANDARD", STANDARD_PLAN_DECODER,
                    "PREMIUM",  PREMIUM_PLAN_DECODER,
                    "CUSTOM",   CUSTOM_PLAN_DECODER
            ));
}
