package com.houssen.liberoshop.entity;

public enum OrderStatus {
    NEW("Nouvelle"),
    PAID("Payée"),
    DELIVERED("Livrée"),
    CANCELLED("Annulée");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    /** Libellé affiché sur le PDF. */
    public String label() {
        return label;
    }
}
