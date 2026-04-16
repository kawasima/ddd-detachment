package com.example.mealse.domain;

public record MealSetId(String value) {
    public MealSetId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("MealSetId must not be blank");
        }
    }
}
