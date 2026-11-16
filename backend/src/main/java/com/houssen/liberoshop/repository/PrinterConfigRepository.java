package com.houssen.liberoshop.repository;


import com.houssen.liberoshop.entity.PrinterConfig;
import com.houssen.liberoshop.entity.PrinterType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PrinterConfigRepository extends JpaRepository<PrinterConfig, String> {

    Optional<PrinterConfig> findByTypeAndAddressIgnoreCase(PrinterType type, String address);
}
