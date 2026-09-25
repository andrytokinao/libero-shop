package com.houssen.libertyshop.repository;

import com.houssen.libertyshop.entity.CashRemittance;
import com.houssen.libertyshop.entity.RemittanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface CashRemittanceRepository extends JpaRepository<CashRemittance, Long> {

    @Query("""
            select r from CashRemittance r
              join fetch r.submittedBy
              left join fetch r.confirmedBy
            where (:status is null or r.status = :status)
              and (:submittedById is null or r.submittedBy.id = :submittedById)
            order by r.remittanceDate desc, r.id desc
            """)
    List<CashRemittance> search(@Param("status") RemittanceStatus status,
                                @Param("submittedById") Long submittedById);

    @Query("select coalesce(sum(r.amount), 0) from CashRemittance r where r.status = :status")
    BigDecimal sumByStatus(@Param("status") RemittanceStatus status);

    @Query("""
            select coalesce(sum(r.amount), 0) from CashRemittance r
            where r.status = :status and r.remittanceDate >= :from and r.remittanceDate < :to
            """)
    BigDecimal sumByStatusAndPeriod(@Param("status") RemittanceStatus status,
                                    @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to);

    long countBySubmittedById(Long submittedById);
}
