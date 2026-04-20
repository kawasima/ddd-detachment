package com.example.mealse.ch11;

import com.example.mealse.domain.DeliveryFrequency;
import com.example.mealse.domain.MealSetId;
import com.example.mealse.domain.OrderPlan;
import com.example.mealse.domain.Subscription;
import com.example.mealse.domain.SubscriptionId;
import com.example.mealse.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the HTTP-level behavior of {@link OrderController}:
 * status codes, response bodies, and path variable handling.
 *
 * <p>Uses a standalone {@link MockMvc} setup so the tests do not depend on a
 * full Spring Boot application context.</p>
 */
class OrderControllerTest {

    private InMemorySubscriptionRepository repository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        repository = new InMemorySubscriptionRepository();
        mockMvc = MockMvcBuilders.standaloneSetup(new OrderController(repository)).build();
    }

    @Test
    void createOrder_returns201_withEncodedBody() throws Exception {
        String body = """
                {
                    "userId": "user-001",
                    "planType": "STANDARD",
                    "mealSetId": "set-001",
                    "frequency": "WEEKLY"
                }
                """;

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.userId").value("user-001"))
                .andExpect(jsonPath("$.plan.planType").value("STANDARD"))
                .andExpect(jsonPath("$.plan.mealSetId").value("set-001"))
                .andExpect(jsonPath("$.plan.frequency").value("WEEKLY"));
    }

    @Test
    void createOrder_returns400_whenDecodeFails() throws Exception {
        String body = """
                {
                    "userId": "user-001",
                    "planType": "UNKNOWN"
                }
                """;

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getOrder_returns200_withEncodedBody_whenFound() throws Exception {
        Subscription.Active active = new Subscription.Active(
                new SubscriptionId("sub-200"),
                new UserId("user-200"),
                new OrderPlan.StandardPlan(new MealSetId("set-200"), DeliveryFrequency.WEEKLY),
                DeliveryFrequency.WEEKLY,
                LocalDate.of(2026, 5, 1)
        );
        repository.save(active);

        mockMvc.perform(get("/orders/{id}", "sub-200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("sub-200"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.nextDeliveryDate").value("2026-05-01"));
    }

    @Test
    void getOrder_returns404_whenNotFound() throws Exception {
        mockMvc.perform(get("/orders/{id}", "sub-missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOrder_returns400_whenIdIsBlank() throws Exception {
        // 空白のパス変数 (例: "/orders/ ") では SubscriptionId コンストラクタが例外を投げる。
        // Controller はこれを 400 にマッピングする。
        mockMvc.perform(get("/orders/{id}", " "))
                .andExpect(status().isBadRequest());
    }
}
