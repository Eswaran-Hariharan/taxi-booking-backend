package com.uber.payment.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Slf4j
@Component
public class StripePaymentGateway implements PaymentGateway {

    @Value("${stripe.api.key:sk_test_placeholder}")
    private String apiKey;

    @Value("${stripe.api.base-url:https://api.stripe.com/v1}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public ChargeResult charge(String customerId, String paymentMethodId, BigDecimal amount, String idempotencyKey) {
        try {
            long amountCents = amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("amount", amountCents);
            params.put("currency", "usd");
            params.put("customer", customerId);
            params.put("payment_method", paymentMethodId);
            params.put("confirm", "true");
            params.put("off_session", "true");

            HttpHeaders headers = buildHeaders(idempotencyKey);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(params, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/payment_intents", HttpMethod.POST, request, Map.class
            );

            Map<String, Object> body = response.getBody();
            if (body != null && "succeeded".equals(body.get("status"))) {
                return new ChargeResult(true, (String) body.get("id"), null, null);
            }
            return new ChargeResult(false, null, "PAYMENT_FAILED", "Payment intent not succeeded");

        } catch (Exception e) {
            log.error("Stripe charge failed for customer {}: {}", customerId, e.getMessage());
            return new ChargeResult(false, null, "GATEWAY_ERROR", e.getMessage());
        }
    }

    @Override
    public RefundResult refund(String transactionId, BigDecimal amount) {
        try {
            long amountCents = amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("payment_intent", transactionId);
            params.put("amount", amountCents);

            HttpHeaders headers = buildHeaders(UUID.randomUUID().toString());
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(params, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/refunds", HttpMethod.POST, request, Map.class
            );

            Map<String, Object> body = response.getBody();
            if (body != null) {
                return new RefundResult(true, (String) body.get("id"), null);
            }
            return new RefundResult(false, null, "REFUND_FAILED");

        } catch (Exception e) {
            return new RefundResult(false, null, e.getMessage());
        }
    }

    @Override
    public String createCustomer(String email, String name) {
        Map<String, Object> params = Map.of("email", email, "name", name);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(params, buildHeaders(null));
        ResponseEntity<Map> response = restTemplate.exchange(
            baseUrl + "/customers", HttpMethod.POST, request, Map.class
        );
        return response.getBody() != null ? (String) response.getBody().get("id") : null;
    }

    @Override
    public String attachPaymentMethod(String customerId, String paymentMethodToken) {
        Map<String, Object> params = Map.of("customer", customerId);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(params, buildHeaders(null));
        restTemplate.exchange(
            baseUrl + "/payment_methods/" + paymentMethodToken + "/attach",
            HttpMethod.POST, request, Map.class
        );
        return paymentMethodToken;
    }

    private HttpHeaders buildHeaders(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Authorization", "Bearer " + apiKey);
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return headers;
    }
}
