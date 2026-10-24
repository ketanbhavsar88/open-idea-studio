package org.openideastudio.ai;

import java.util.List;

/** The model answered, but never with output that passed validation within the retry budget. */
public class InvalidModelOutputException extends RuntimeException {

    private final List<String> errors;

    public InvalidModelOutputException(List<String> errors) {
        super("The AI response could not be used: " + String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
