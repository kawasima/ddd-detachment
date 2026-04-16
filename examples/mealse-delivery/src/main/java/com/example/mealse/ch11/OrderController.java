package com.example.mealse.ch11;

import com.example.mealse.ch04.OrderPlanDecoder;
import com.example.mealse.domain.OrderPlan;
import com.example.mealse.domain.Subscription;
import com.example.mealse.domain.SubscriptionId;
import com.example.mealse.domain.UserId;
import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Result;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Controller that handles order creation requests.
 *
 * <p>This class demonstrates the complete flow described in Chapter 11:</p>
 * <ol>
 *   <li>Receive {@code JsonNode} from the HTTP request body</li>
 *   <li>Decode it into a typed domain model using {@link OrderPlanDecoder}</li>
 *   <li>Pass the domain model directly to the repository (no intermediate DTO)</li>
 * </ol>
 *
 * <p>Because the decoder returns either a typed {@link OrderPlan} or structured errors,
 * there is no separate validation step. The domain object is always valid by the time
 * it reaches the repository.</p>
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final SubscriptionRepository subscriptionRepository;

    /**
     * Creates a new {@code OrderController}.
     *
     * @param subscriptionRepository the repository used to persist subscriptions
     */
    public OrderController(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    /**
     * Creates a new subscription from the given order request.
     *
     * <p>The request body is decoded directly into an {@link OrderPlan} using the
     * Raoh decoder. If decoding fails (invalid planType, missing fields, etc.),
     * a 400 response is returned with structured error information.
     * If decoding succeeds, a {@link Subscription.Active} is created and persisted.</p>
     *
     * @param body the raw JSON request body
     * @return 201 Created on success, 400 Bad Request on validation failure
     */
    @PostMapping
    public ResponseEntity<?> createOrder(@RequestBody JsonNode body) {
        Result<OrderPlan> result = OrderPlanDecoder.ORDER_PLAN_DECODER.decode(body);

        return switch (result) {
            case Ok<OrderPlan> ok -> {
                OrderPlan plan = ok.value();
                Subscription.Active subscription = new Subscription.Active(
                        new SubscriptionId(UUID.randomUUID().toString()),
                        extractUserId(body),
                        plan,
                        plan.frequency(),
                        LocalDate.now().plusWeeks(1)
                );
                subscriptionRepository.save(subscription);
                yield ResponseEntity.status(201).build();
            }
            case Err<OrderPlan> err ->
                    ResponseEntity.badRequest().body(err.issues());
        };
    }

    private UserId extractUserId(JsonNode body) {
        return new UserId(body.path("userId").asText());
    }
}
