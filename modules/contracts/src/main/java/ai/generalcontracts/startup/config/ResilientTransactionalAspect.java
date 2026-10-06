package ai.generalcontracts.startup.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class ResilientTransactionalAspect {

    private final RetryRegistry retryRegistry;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Around("@annotation(resilientTransactional)")
    public Object aroundResilientTransactional(ProceedingJoinPoint joinPoint, ResilientTransactional resilientTransactional) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        
        Retry retry = retryRegistry.retry(resilientTransactional.retryName(), methodName);
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(resilientTransactional.circuitBreakerName(), methodName);

        log.info("Executing resilient transactional method: {} with retry: {} and circuit breaker: {}", 
                methodName, resilientTransactional.retryName(), resilientTransactional.circuitBreakerName());

        return CircuitBreaker.decorateSupplier(circuitBreaker,
                Retry.decorateSupplier(retry, () -> {
                    try {
                        return joinPoint.proceed();
                    } catch (Throwable throwable) {
                        throw new RuntimeException(throwable);
                    }
                })).get();
    }
}
