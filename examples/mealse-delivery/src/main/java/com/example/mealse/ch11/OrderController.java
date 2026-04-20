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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Controller that handles order creation and retrieval requests.
 *
 * <p>This class demonstrates the complete flow described in Chapter 11:</p>
 * <ol>
 *   <li>Receive {@code JsonNode} from the HTTP request body</li>
 *   <li>Decode it into a typed domain model using {@link OrderPlanDecoder}</li>
 *   <li>Pass the domain model directly to the repository (no intermediate DTO)</li>
 *   <li>Encode the domain model back into a {@code Map<String, Object>} response
 *       using {@link SubscriptionEncoder}</li>
 * </ol>
 *
 * <p>The input and output sides are symmetric: decoders convert external
 * representations into domain types, encoders convert domain types into external
 * representations. No intermediate request or response DTO classes are needed.</p>
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final SubscriptionRepository subscriptionRepository;

    /**
     * Creates a new {@code OrderController}.
     *
     * @param subscriptionRepository the repository used to persist and retrieve subscriptions
     */
    public OrderController(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    /**
     * Creates a new subscription from the given order request.
     *
     * <p>The request body is decoded directly into an {@link OrderPlan} using the
     * Raoh decoder. If decoding fails, a 400 response is returned with structured
     * error information. If decoding succeeds, a {@link Subscription.Active} is
     * created, persisted, and encoded back into a {@code Map<String, Object>} for
     * the response body.</p>
     *
     * @param body the raw JSON request body
     * @return 201 Created with the encoded subscription on success,
     *         400 Bad Request with the issues on validation failure
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
                Map<String, Object> responseBody =
                        SubscriptionEncoder.SUBSCRIPTION_ENCODER.encode(subscription);
                yield ResponseEntity.status(201).body(responseBody);
            }
            case Err<OrderPlan> err ->
                    ResponseEntity.badRequest().body(err.issues());
        };
    }

    /**
     * Retrieves a subscription by ID.
     *
     * <p>The domain model is encoded into a {@code Map<String, Object>} for the
     * response body. No separate response DTO class is defined; the encoder
     * describes the output shape declaratively.</p>
     *
     * @param id the subscription ID
     * @return 200 OK with the encoded subscription, 400 Bad Request for an invalid ID,
     *         or 404 Not Found
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getOrder(@PathVariable("id") String id) {
        final SubscriptionId subscriptionId;
        try {
            subscriptionId = new SubscriptionId(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        return subscriptionRepository.findById(subscriptionId)
                .<ResponseEntity<?>>map(subscription ->
                        ResponseEntity.ok(SubscriptionEncoder.SUBSCRIPTION_ENCODER.encode(subscription)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private UserId extractUserId(JsonNode body) {
        return new UserId(body.path("userId").asText());
    }
}
