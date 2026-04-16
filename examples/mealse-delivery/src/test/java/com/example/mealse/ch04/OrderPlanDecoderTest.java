package com.example.mealse.ch04;

import com.example.mealse.domain.DeliveryFrequency;
import com.example.mealse.domain.OrderPlan;
import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;

import static com.example.mealse.ch04.OrderPlanDecoder.ORDER_PLAN_DECODER;
import static org.assertj.core.api.Assertions.assertThat;

class OrderPlanDecoderTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Test
    void standardPlan_success() throws Exception {
        var json = mapper.readTree("""
                {
                  "planType": "STANDARD",
                  "mealSetId": "set-001",
                  "frequency": "WEEKLY"
                }
                """);

        assertThat(ORDER_PLAN_DECODER.decode(json))
                .isInstanceOfSatisfying(Ok.class, ok -> {
                    assertThat(ok.value()).isInstanceOfSatisfying(OrderPlan.StandardPlan.class, plan -> {
                        assertThat(plan.mealSetId().value()).isEqualTo("set-001");
                        assertThat(plan.frequency()).isEqualTo(DeliveryFrequency.WEEKLY);
                    });
                });
    }

    @Test
    void premiumPlan_success() throws Exception {
        var json = mapper.readTree("""
                {
                  "planType": "PREMIUM",
                  "mealSetId": "set-002",
                  "frequency": "BIWEEKLY",
                  "includeFrozen": true
                }
                """);

        assertThat(ORDER_PLAN_DECODER.decode(json))
                .isInstanceOfSatisfying(Ok.class, ok -> {
                    assertThat(ok.value()).isInstanceOfSatisfying(OrderPlan.PremiumPlan.class, plan -> {
                        assertThat(plan.includeFrozen()).isTrue();
                    });
                });
    }

    @Test
    void customPlan_success() throws Exception {
        var startDate = LocalDate.now().plusDays(5);
        var json = mapper.readTree("""
                {
                  "planType": "CUSTOM",
                  "meals": ["meal-a", "meal-b"],
                  "frequency": "WEEKLY",
                  "startDate": "%s"
                }
                """.formatted(startDate));

        assertThat(ORDER_PLAN_DECODER.decode(json))
                .isInstanceOfSatisfying(Ok.class, ok -> {
                    assertThat(ok.value()).isInstanceOfSatisfying(OrderPlan.CustomPlan.class, plan -> {
                        assertThat(plan.meals()).hasSize(2);
                        assertThat(plan.startDate()).isEqualTo(startDate);
                    });
                });
    }

    @Test
    void customPlan_startDateTooSoon_fails() throws Exception {
        var json = mapper.readTree("""
                {
                  "planType": "CUSTOM",
                  "meals": ["meal-a"],
                  "frequency": "WEEKLY",
                  "startDate": "%s"
                }
                """.formatted(LocalDate.now().plusDays(1)));

        assertThat(ORDER_PLAN_DECODER.decode(json))
                .isInstanceOf(Err.class);
    }

    @Test
    void standardPlan_missingMealSetId_fails() throws Exception {
        var json = mapper.readTree("""
                {
                  "planType": "STANDARD",
                  "frequency": "WEEKLY"
                }
                """);

        assertThat(ORDER_PLAN_DECODER.decode(json))
                .isInstanceOf(Err.class);
    }

    @Test
    void unknownPlanType_fails() throws Exception {
        var json = mapper.readTree("""
                {
                  "planType": "UNKNOWN",
                  "frequency": "WEEKLY"
                }
                """);

        assertThat(ORDER_PLAN_DECODER.decode(json))
                .isInstanceOf(Err.class);
    }
}
