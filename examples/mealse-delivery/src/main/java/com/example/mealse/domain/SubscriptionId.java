package com.example.mealse.domain;

public record SubscriptionId(String value) {
    public SubscriptionId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("SubscriptionId must not be blank");
        }
    }
}
