package com.pacioli.core.enums;

public enum AuditAction {
    // Actions CRUD
    CREATE("CREATE"),
    UPDATE("UPDATE"),
    DELETE("DELETE"),
    VIEW("VIEW"),

    // Actions d'authentification
    LOGIN("LOGIN"),
    LOGOUT("LOGOUT"),
    LOGIN_FAILED("LOGIN_FAILED"),

    // Actions spécifiques
    UPLOAD("UPLOAD"),
    DOWNLOAD("DOWNLOAD"),
    EXPORT("EXPORT"),
    IMPORT("IMPORT"),
    VALIDATE("VALIDATE"),
    REJECT("REJECT"),
    APPROVE("APPROVE"),

    // Actions de configuration
    CONFIGURE("CONFIGURE"),
    ACTIVATE("ACTIVATE"),
    DEACTIVATE("DEACTIVATE"),

    // Actions de conversion
    CONVERT("CONVERT"),
    CALCULATE("CALCULATE");

    private final String value;

    AuditAction(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}