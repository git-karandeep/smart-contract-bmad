package ai.generalcontracts.startup.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ResilientTransactional {
    String retryName() default "transactionalRetry";
    String circuitBreakerName() default "transactionalCircuitBreaker";
}
