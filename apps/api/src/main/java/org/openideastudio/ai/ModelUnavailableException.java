package org.openideastudio.ai;

/** The model could not produce a response. {@link Reason} drives the user-facing error state. */
public class ModelUnavailableException extends RuntimeException {

    public enum Reason {
        NOT_CONFIGURED("model-not-configured"),
        RATE_LIMITED("model-rate-limited"),
        TIMEOUT("model-timeout"),
        UNAVAILABLE("model-unavailable");

        private final String code;

        Reason(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    private final Reason reason;

    public ModelUnavailableException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public ModelUnavailableException(Reason reason, String message) {
        this(reason, message, null);
    }

    public Reason reason() {
        return reason;
    }
}
