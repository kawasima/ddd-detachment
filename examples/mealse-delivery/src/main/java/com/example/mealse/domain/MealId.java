package com.example.mealse.domain;

public record MealId(String value) {
    public MealId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("MealId must not be blank");
        }
    }
}
