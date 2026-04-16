package com.example.mealse.ch03;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = OrderPlanFormValidator.class)
@Documented
public @interface ValidOrderPlanForm {
    String message() default "プランタイプに応じた必須項目が入力されていません";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
