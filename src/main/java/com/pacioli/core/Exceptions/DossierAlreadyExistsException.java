package com.pacioli.core.Exceptions;

public class DossierAlreadyExistsException extends RuntimeException {
    public DossierAlreadyExistsException(String message) {
        super(message);
    }
}
