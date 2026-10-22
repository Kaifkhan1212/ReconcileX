package com.reconcilex.service;

import java.util.Collections;
import java.util.List;

public class ValidationResult<T> {

    private final T data;
    private final List<RowValidationError> errors;

    private ValidationResult(T data, List<RowValidationError> errors) {
        this.data = data;
        this.errors = errors != null ? errors : Collections.emptyList();
    }

    public static <T> ValidationResult<T> valid(T data) {
        return new ValidationResult<>(data, Collections.emptyList());
    }

    public static <T> ValidationResult<T> invalid(List<RowValidationError> errors) {
        return new ValidationResult<>(null, errors);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public T getData() {
        return data;
    }

    public List<RowValidationError> getErrors() {
        return errors;
    }
}
