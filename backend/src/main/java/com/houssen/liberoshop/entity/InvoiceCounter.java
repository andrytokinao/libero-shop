package com.houssen.liberoshop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The next invoice number, kept in one row and moved within the sale's own transaction: a sale
 * that fails gives its number back, so the numbers follow one another without gaps.
 */
@Entity
@Table(name = "invoice_counter")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceCounter {

    /** The single row. */
    public static final long SINGLE = 1L;

    @Id
    private Long id;

    @Column(name = "next_number", nullable = false)
    private long nextNumber;

    /** Takes the next number and moves the counter on. */
    public long take() {
        return nextNumber++;
    }
}
