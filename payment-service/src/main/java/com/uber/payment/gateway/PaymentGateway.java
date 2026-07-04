package com.uber.payment.gateway;

import java.math.BigDecimal;

public interface PaymentGateway {
    ChargeResult charge(String customerId, String paymentMethodId, BigDecimal amount, String idempotencyKey);
    RefundResult refund(String transactionId, BigDecimal amount);
    String createCustomer(String email, String name);
    String attachPaymentMethod(String customerId, String paymentMethodToken);

    record ChargeResult(boolean success, String transactionId, String failureCode, String failureMessage) {}
    record RefundResult(boolean success, String refundId, String failureMessage) {}
}
