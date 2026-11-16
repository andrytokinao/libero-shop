package com.houssen.liberoshop.entity;

public record PrintJob(String title, byte[] payload, PayloadKind kind) {

    public static PrintJob escpos(String title, byte[] data) {
        return new PrintJob(title, data, PayloadKind.ESCPOS);
    }

    public static PrintJob pdf(String title, byte[] data) {
        return new PrintJob(title, data, PayloadKind.PDF);
    }
}
