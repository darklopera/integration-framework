package com.channel.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.channel.integration.config.IntegrationProperties;

/**
 * Entry point for the Demo Service (Section C - Technical Assessment).
 *
 * <p>Run with: {@code mvn spring-boot:run -pl demo}</p>
 * <p>Or: {@code java -jar demo/target/integration-demo-1.0.0.jar}</p>
 *
 * <p>Once running, use the following endpoints to observe resilience patterns:</p>
 * <pre>
 *  # Process a payment (random 50% failure rate):
 *  curl -X POST "http://localhost:8080/demo/payment"
 *
 *  # Process with idempotency key (second call returns cached):
 *  curl -X POST "http://localhost:8080/demo/payment?idempotencyKey=my-key-001"
 *  curl -X POST "http://localhost:8080/demo/payment?idempotencyKey=my-key-001"
 *
 *  # Force all failures to trip the circuit breaker:
 *  curl -X PUT "http://localhost:8080/demo/mode/ALWAYS_FAIL"
 *
 *  # Check circuit breaker state:
 *  curl "http://localhost:8080/demo/circuit-breaker/state"
 *
 *  # Simulate timeout:
 *  curl -X PUT "http://localhost:8080/demo/mode/SLOW_RESPONSE"
 *
 *  # Recover the upstream:
 *  curl -X PUT "http://localhost:8080/demo/mode/HEALTHY"
 *  curl -X POST "http://localhost:8080/demo/circuit-breaker/reset"
 * </pre>
 */
@SpringBootApplication(scanBasePackages = {"com.channel.demo", "com.channel.integration"})
@EnableConfigurationProperties(IntegrationProperties.class)
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
