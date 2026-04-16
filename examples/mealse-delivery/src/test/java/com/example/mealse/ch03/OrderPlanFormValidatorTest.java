package com.example.mealse.ch03;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OrderPlanFormValidatorTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void standardPlan_valid() {
        var form = new OrderPlanForm();
        form.setPlanType("STANDARD");
        form.setFrequency("WEEKLY");
        form.setMealSetId("set-001");

        Set<ConstraintViolation<OrderPlanForm>> violations = validator.validate(form);
        assertThat(violations).isEmpty();
    }

    @Test
    void standardPlan_missingMealSetId_invalid() {
        var form = new OrderPlanForm();
        form.setPlanType("STANDARD");
        form.setFrequency("WEEKLY");

        Set<ConstraintViolation<OrderPlanForm>> violations = validator.validate(form);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("mealSetId"));
    }

    @Test
    void premiumPlan_valid() {
        var form = new OrderPlanForm();
        form.setPlanType("PREMIUM");
        form.setFrequency("BIWEEKLY");
        form.setMealSetId("set-002");
        form.setIncludeFrozen(true);

        Set<ConstraintViolation<OrderPlanForm>> violations = validator.validate(form);
        assertThat(violations).isEmpty();
    }

    @Test
    void premiumPlan_missingIncludeFrozen_invalid() {
        var form = new OrderPlanForm();
        form.setPlanType("PREMIUM");
        form.setFrequency("WEEKLY");
        form.setMealSetId("set-002");
        // includeFrozen が null

        Set<ConstraintViolation<OrderPlanForm>> violations = validator.validate(form);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("includeFrozen"));
    }

    @Test
    void customPlan_valid() {
        var form = new OrderPlanForm();
        form.setPlanType("CUSTOM");
        form.setFrequency("WEEKLY");
        form.setMealIds(List.of("meal-a", "meal-b"));
        form.setStartDate(LocalDate.now().plusDays(5));

        Set<ConstraintViolation<OrderPlanForm>> violations = validator.validate(form);
        assertThat(violations).isEmpty();
    }

    @Test
    void customPlan_startDateTooSoon_invalid() {
        var form = new OrderPlanForm();
        form.setPlanType("CUSTOM");
        form.setFrequency("WEEKLY");
        form.setMealIds(List.of("meal-a"));
        form.setStartDate(LocalDate.now().plusDays(1));

        Set<ConstraintViolation<OrderPlanForm>> violations = validator.validate(form);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("startDate"));
    }
}
