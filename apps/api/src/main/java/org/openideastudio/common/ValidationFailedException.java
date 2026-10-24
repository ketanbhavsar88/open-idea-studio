package org.openideastudio.common;

import java.util.List;

/** Domain validation failed; {@code errors} are shown to the user. */
public class ValidationFailedException extends RuntimeException {

    private final String code;
    private final List<String> errors;

    public ValidationFailedException(String code, String message, List<String> errors) {
        super(message);
        this.code = code;
        this.errors = List.copyOf(errors);
    }

    public String code() {
        return code;
    }

    public List<String> errors() {
        return errors;
    }
}
