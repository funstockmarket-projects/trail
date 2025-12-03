package models;

public class ValidationResult {

    private boolean valid;
    private String message;

    public ValidationResult(boolean valid, String message) {
        this.valid = valid;
        this.message = message;
    }

    public static ValidationResult pass() {
        return new ValidationResult(true, "OK");
    }

    public static ValidationResult fail(String msg) {
        return new ValidationResult(false, msg);
    }

    public boolean isValid() {
        return valid;
    }

    public String getMessage() {
        return message;
    }
}