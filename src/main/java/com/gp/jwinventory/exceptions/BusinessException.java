package com.gp.jwinventory.exceptions;

/** A rule violation whose message is shown to the user as-is (Italian). */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
