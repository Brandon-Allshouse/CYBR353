package com.delivery.util;

import java.util.function.Function;

public abstract class Result<T, E> {

    // Private constructor prevents external instantiation
    private Result() {}

    // Success case containing value
    public static final class Ok<T, E> extends Result<T, E> {
        private final T value;

        private Ok(T value) {
            this.value = value;
        }

        public T getValue() {
            return value;
        }
    }

    // Failure case containing error
    public static final class Err<T, E> extends Result<T, E> {
        private final E error;

        private Err(E error) {
            this.error = error;
        }

        public E getError() {
            return error;
        }
    }

    // Factory methods
    public static <T, E> Result<T, E> ok(T value) {
        return new Ok<>(value);
    }

    public static <T, E> Result<T, E> err(E error) {
        return new Err<>(error);
    }

    // Type checking
    public boolean isOk() {
        return this instanceof Ok;
    }

    public boolean isErr() {
        return this instanceof Err;
    }

    // Safe value extraction
    public T unwrap() {
        if (this instanceof Ok) {
            return ((Ok<T, E>) this).getValue();
        }
        throw new RuntimeException("Called unwrap on Err");
    }

    public T unwrapOr(T defaultValue) {
        if (this instanceof Ok) {
            return ((Ok<T, E>) this).getValue();
        }
        return defaultValue;
    }

    public E unwrapErr() {
        if (this instanceof Err) {
            return ((Err<T, E>) this).getError();
        }
        throw new RuntimeException("Called unwrapErr on Ok");
    }

    // Transformation
    public <U> Result<U, E> map(Function<T, U> mapper) {
        if (this instanceof Ok) {
            return ok(mapper.apply(((Ok<T, E>) this).getValue()));
        }
        return err(((Err<T, E>) this).getError());
    }

    public <F> Result<T, F> mapErr(Function<E, F> mapper) {
        if (this instanceof Err) {
            return err(mapper.apply(((Err<T, E>) this).getError()));
        }
        return ok(((Ok<T, E>) this).getValue());
    }
}
