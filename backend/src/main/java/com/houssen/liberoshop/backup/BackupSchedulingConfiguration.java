package com.houssen.liberoshop.backup;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on the scheduler {@link BackupService#tick()} runs on. Unconditional, unlike the license
 * renewal's: backups are wanted on every installation.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class BackupSchedulingConfiguration {
}
