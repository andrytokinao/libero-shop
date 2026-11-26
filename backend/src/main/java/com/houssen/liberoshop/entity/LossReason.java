package com.houssen.liberoshop.entity;

/** Pourquoi une quantité a été perdue. */
public enum LossReason {
    /** Perdue pendant le voyage, avant d'arriver au dépôt. */
    TRANSPORT,
    /** Déchet ou évaporation pendant une transformation. */
    PROCESS,
    /** Péremption ou casse en rayon. */
    SPOILAGE,
    OTHER
}
