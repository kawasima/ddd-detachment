package com.example.mealse.ch03;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;

public class OrderPlanFormValidator
        implements ConstraintValidator<ValidOrderPlanForm, OrderPlanForm> {

    @Override
    public boolean isValid(OrderPlanForm form, ConstraintValidatorContext context) {
        if (form == null || form.getPlanType() == null) {
            return true;
        }
        context.disableDefaultConstraintViolation();

        return switch (form.getPlanType()) {
            case "STANDARD" -> validateStandard(form, context);
            case "PREMIUM"  -> validatePremium(form, context);
            case "CUSTOM"   -> validateCustom(form, context);
            default -> true;
        };
    }

    private boolean validateStandard(OrderPlanForm form, ConstraintValidatorContext context) {
        boolean valid = true;
        if (form.getMealSetId() == null || form.getMealSetId().isBlank()) {
            addViolation(context, "mealSetId", "ミールセットは必須です");
            valid = false;
        }
        return valid;
    }

    private boolean validatePremium(OrderPlanForm form, ConstraintValidatorContext context) {
        boolean valid = true;
        if (form.getMealSetId() == null || form.getMealSetId().isBlank()) {
            addViolation(context, "mealSetId", "ミールセットは必須です");
            valid = false;
        }
        if (form.getIncludeFrozen() == null) {
            addViolation(context, "includeFrozen", "冷凍オプションの選択は必須です");
            valid = false;
        }
        return valid;
    }

    private boolean validateCustom(OrderPlanForm form, ConstraintValidatorContext context) {
        boolean valid = true;
        if (form.getMealIds() == null || form.getMealIds().isEmpty()) {
            addViolation(context, "mealIds", "食材を1つ以上選択してください");
            valid = false;
        }
        if (form.getStartDate() == null) {
            addViolation(context, "startDate", "開始日は必須です");
            valid = false;
        } else if (form.getStartDate().isBefore(LocalDate.now().plusDays(3))) {
            addViolation(context, "startDate", "開始日は3日以上先の日付を指定してください");
            valid = false;
        }
        return valid;
    }

    private void addViolation(ConstraintValidatorContext context,
                              String property, String message) {
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(property)
                .addConstraintViolation();
    }
}
