package com.echogallery.card;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CardContentValidator.class)
public @interface ValidCardContent {
    String message() default "卡片內容格式不正確";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
