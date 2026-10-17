package vn.tramsach.payments.domain;

import java.util.UUID;

public record SavedDemoCard(UUID buyerId, String customerId, String paymentMethodId) {}
