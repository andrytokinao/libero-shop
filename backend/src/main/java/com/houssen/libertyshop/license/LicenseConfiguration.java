package com.houssen.libertyshop.license;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

import java.time.Clock;

/** Wires the license module. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LicenseProperties.class)
public class LicenseConfiguration {

    /**
     * Verification is always done against the compiled-in key. There is deliberately no
     * way to override it from configuration.
     */
    @Bean
    public LicenseVerifier licenseVerifier() {
        return new LicenseVerifier(EmbeddedLicenseKey.publicKey());
    }

    /**
     * Injectable clock, so date-sensitive behaviour stays testable. Conditional, so a
     * test can declare a fixed {@code Clock} and drive the application to any date.
     */
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock licenseClock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public LicenseService licenseService(LicenseVerifier verifier, LicenseProperties properties, Clock clock) {
        return new LicenseService(verifier, properties, clock, MachineFingerprint.current());
    }

    /**
     * Declares the enforcement advisor.
     *
     * <p>Kept in its own configuration class marked as infrastructure. Advisors are built
     * while bean post-processors are still being created, so whichever class declares one
     * gets instantiated in that early phase; isolating it here keeps
     * {@link LicenseConfiguration} out of it, along with the beans it declares.
     */
    @Configuration(proxyBeanMethods = false)
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static class EnforcementConfiguration {

        /**
         * The service is taken through an {@link ObjectProvider} rather than injected, so
         * it is looked up on the first guarded call instead of being created early.
         */
        @Bean
        @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
        LicenseEnforcementAdvisor licenseEnforcementAdvisor(ObjectProvider<LicenseService> licenseService) {
            return new LicenseEnforcementAdvisor(licenseService::getObject);
        }
    }

    /**
     * Online renewal. Off unless an endpoint is configured, so a fresh development
     * checkout never tries to phone home.
     */
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(prefix = "libertyshop.license.renewal", name = "enabled", havingValue = "true")
    static class RenewalConfiguration {

        @Bean
        public RestClient licenseRestClient(LicenseProperties properties) {
            // Short, explicit timeouts: an unreachable server must not hold a worker
            // thread while the shop is serving customers.
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(properties.renewal().timeout());
            factory.setReadTimeout(properties.renewal().timeout());
            return RestClient.builder().requestFactory(factory).build();
        }

        @Bean
        public LicenseRenewalService licenseRenewalService(LicenseService licenseService,
                                                           LicenseProperties properties,
                                                           RestClient licenseRestClient,
                                                           Clock clock) {
            return new LicenseRenewalService(licenseService, properties.renewal(), licenseRestClient, clock);
        }
    }
}
