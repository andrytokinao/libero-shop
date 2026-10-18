package com.houssen.liberoshop.repository;

import com.houssen.liberoshop.entity.InvoiceCounter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InvoiceCounterRepository extends JpaRepository<InvoiceCounter, Long> {

    /** Locked until the sale commits: two tills numbering at once take turns, never the same number. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from InvoiceCounter c where c.id = :id")
    Optional<InvoiceCounter> findForUpdate(@Param("id") Long id);
}
