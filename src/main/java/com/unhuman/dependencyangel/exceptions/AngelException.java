package com.unhuman.dependencyangel.exceptions;

public class AngelException extends RuntimeException {
    private String details;
    private String extraMessage;
    public AngelException(String message, String details, String extraMessage) {
        super(message);
        this.details = details;
        this.extraMessage = extraMessage;
    }

    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder("Error: ").append(super.getMessage());
        if (details != null) {
            sb.append("\nDetails: ").append(details);
        }
        if (extraMessage != null) {
            sb.append("\n").append(extraMessage);
        }
        return sb.toString();
    }
}
