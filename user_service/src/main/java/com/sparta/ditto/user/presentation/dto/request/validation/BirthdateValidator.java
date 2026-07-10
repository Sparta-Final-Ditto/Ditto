package com.sparta.ditto.user.presentation.dto.request.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.LocalDate;

public class BirthdateValidator implements ConstraintValidator<ValidBirthdate, LocalDate> {

    private static final LocalDate MIN_BIRTHDATE = LocalDate.of(1900, 1, 1);

    @Override
    public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return value.isAfter(MIN_BIRTHDATE) || value.isEqual(MIN_BIRTHDATE);
    }
}
