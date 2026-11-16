package com.houssen.liberoshop.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Auto-configuration : ajouter la dépendance suffit. Les entités et repositories du module
 * sont enregistrés sans écraser ceux de l'application hôte.
 */
@AutoConfiguration
@AutoConfigurationPackage(basePackages = "com.houssen.liberoshop")
@ComponentScan(basePackages = "com.houssen.liberoshop",
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = PrintingConfiguration.class))
public class PrintingConfiguration {
}
