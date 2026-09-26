package com.houssen.liberoshop.license;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.util.function.Supplier;

/**
 * Applies {@link RequiresActiveLicense} by refusing the call when the license no longer
 * allows writes.
 *
 * <p>Built from plain Spring AOP (a pointcut plus an interceptor) rather than an
 * {@code @Aspect}. It behaves identically but does not depend on AspectJ being on the
 * classpath or on {@code @EnableAspectJAutoProxy} being switched on by another
 * auto-configuration -- one less thing that can silently stop enforcing.
 *
 * <p>The license check is resolved lazily on each invocation so that enforcement follows
 * the current state: a license renewed while the application is running lifts the block
 * without a restart.
 *
 * <p>The {@link LicenseService} is taken as a {@link Supplier} rather than as an instance.
 * An advisor is created early, while bean post-processors are still being set up, so
 * injecting the service directly would drag the whole license module into that phase and
 * make those beans ineligible for post-processing. Looking it up on first use avoids that
 * entirely.
 */
public class LicenseEnforcementAdvisor extends DefaultPointcutAdvisor {

    /**
     * @param licenseService lazy handle on the service; resolved on the first advised call
     */
    public LicenseEnforcementAdvisor(Supplier<LicenseService> licenseService) {
        super(pointcut(), interceptor(licenseService));
        // Infrastructure advisors should not themselves be proxied or post-processed.
        setOrder(Integer.MIN_VALUE + 100);
    }

    /** Convenience for tests and manual wiring, where the service already exists. */
    public LicenseEnforcementAdvisor(LicenseService licenseService) {
        this(() -> licenseService);
    }

    /** Matches a method annotated directly, or any method of an annotated class. */
    private static StaticMethodMatcherPointcut pointcut() {
        return new StaticMethodMatcherPointcut() {
            @Override
            public boolean matches(Method method, Class<?> targetClass) {
                // Resolve against the implementation: the annotation usually sits on the
                // class, not on the interface the caller sees.
                Method specific = AopUtils.getMostSpecificMethod(method, targetClass);
                return AnnotatedElementUtils.hasAnnotation(specific, RequiresActiveLicense.class)
                        || AnnotatedElementUtils.hasAnnotation(specific.getDeclaringClass(), RequiresActiveLicense.class)
                        || AnnotatedElementUtils.hasAnnotation(targetClass, RequiresActiveLicense.class);
            }
        };
    }

    private static MethodInterceptor interceptor(Supplier<LicenseService> licenseService) {
        return invocation -> {
            licenseService.get().assertWriteAllowed();
            return invocation.proceed();
        };
    }
}
