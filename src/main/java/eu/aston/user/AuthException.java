package eu.aston.user;

public class AuthException extends RuntimeException {
    private final boolean forbidden;

    public AuthException(String message, boolean forbidden) {
        super(message);
        this.forbidden = forbidden;
    }

    public boolean isForbidden() {
        return forbidden;
    }
}
