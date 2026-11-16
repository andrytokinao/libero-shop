package com.houssen.liberoshop.repository;


import com.houssen.liberoshop.entity.PrinterAssignment;
import com.houssen.liberoshop.entity.PrinterUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrinterAssignmentRepository extends JpaRepository<PrinterAssignment, PrinterUsage> {

    List<PrinterAssignment> findByPrinterId(String printerId);

    void deleteByPrinterId(String printerId);
}
