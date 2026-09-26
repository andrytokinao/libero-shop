package com.houssen.liberoshop.license;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an operation that may only run while the license still allows writes.
 *
 * <p>Put it on the service methods that create business records -- new sales, new
 * invoices, stock movements -- or on the whole service class when every method writes.
 * Once the license is past expiry and past its grace period, calls throw
 * {@code LicenseExpiredException} and the application is effectively read-only: existing
 * sales, stock and invoices stay fully consultable.
 *
 * <pre>
 * &#64;Service
 * public class SaleService {
 *
 *     &#64;RequiresActiveLicense
 *     public Sale record(Sale sale) { ... }   // blocked once read-only
 *
 *     public List&lt;Sale&gt; findByDay(LocalDate day) { ... }   // always available
 * }
 * </pre>
 *
 * <p>Enforced by {@link LicenseEnforcementAdvisor} through a Spring AOP proxy, so it only
 * applies to calls arriving from outside the bean. A method calling another method of the
 * same instance bypasses the proxy -- annotate the entry point that controllers actually
 * call.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface RequiresActiveLicense {
}
