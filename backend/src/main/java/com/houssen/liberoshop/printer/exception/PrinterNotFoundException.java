package com.houssen.liberoshop.printer.exception;

public class PrinterNotFoundException extends RuntimeException {
    public PrinterNotFoundException(String message) {
        super(message);
    }
}
