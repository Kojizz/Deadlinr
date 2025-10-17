package com.deadlinr.app.exception;

/** The presented refresh token is unknown, expired, revoked or reused. */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
