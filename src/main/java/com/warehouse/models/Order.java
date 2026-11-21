package com.warehouse.models;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class Order {
    private final String id;
    private final Map<String, Integer> productQuantities;
    private volatile OrderStatus status;

    public Order(String id, Map<String, Integer> productQuantities) {
        this.id = Objects.requireNonNull(id);
        this.productQuantities = new LinkedHashMap<>(Objects.requireNonNull(productQuantities));
        this.status = OrderStatus.PENDING;
    }

    public String getId() {
        return id;
    }

    public Map<String, Integer> getProductQuantities() {
        return Collections.unmodifiableMap(productQuantities);
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = Objects.requireNonNull(status);
    }
}
