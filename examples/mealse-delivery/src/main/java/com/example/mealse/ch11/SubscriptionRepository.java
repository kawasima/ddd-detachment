package com.example.mealse.ch11;

import com.example.mealse.domain.Subscription;
import com.example.mealse.domain.SubscriptionId;
import com.example.mealse.domain.UserId;

import java.util.Optional;

/**
 * Repository interface for subscriptions.
 *
 * <p>Defined in the domain layer using domain model types.
 * The implementation detail of persistence (SQL, mapping) lives in the implementing class,
 * not here. This is the Dependency Inversion Principle applied to persistence.</p>
 */
public interface SubscriptionRepository {

    /**
     * Persists a subscription (insert or update).
     *
     * @param subscription the subscription to save
     */
    void save(Subscription subscription);

    /**
     * Finds an active subscription by its ID.
     *
     * @param id the subscription ID
     * @return the active subscription, or empty if not found or not active
     */
    Optional<Subscription.Active> findActive(SubscriptionId id);

    /**
     * Finds a suspended subscription by its ID.
     *
     * @param id the subscription ID
     * @return the suspended subscription, or empty if not found or not suspended
     */
    Optional<Subscription.Suspended> findSuspended(SubscriptionId id);
}
