package com.warehouse.simulation;

import com.warehouse.environment.Position;
import com.warehouse.models.Product;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class StockInventory {
    private final Map<String, Integer> quantities = new ConcurrentHashMap<>();
    private final Map<String, Product> products = new ConcurrentHashMap<>();
    private final Map<String, Integer> reservations = new ConcurrentHashMap<>();

    public void addStock(Product product, int quantity) {
        Objects.requireNonNull(product);
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }
        products.put(product.getId(), product);
        quantities.merge(product.getId(), quantity, Integer::sum);
    }

    public synchronized boolean reserve(String productId, int quantity, String orderId) {
        if (quantity <= 0) {
            return false;
        }
        int available = quantities.getOrDefault(productId, 0)
                - reservations.getOrDefault(productId, 0);
        if (available < quantity) {
            return false;
        }
        reservations.merge(productId, quantity, Integer::sum);
        reservations.put(productId + "#" + orderId,
                reservations.getOrDefault(productId + "#" + orderId, 0) + quantity);
        return true;
    }

    public synchronized void commitReservation(String productId,
                                                int quantity,
                                                String orderId) {
        String reservationKey = productId + "#" + orderId;
        reservations.computeIfPresent(reservationKey, (key, value)
                -> value <= quantity ? null : value - quantity);
        reservations.computeIfPresent(productId, (key, value)
                -> value <= quantity ? null : value - quantity);
        quantities.computeIfPresent(productId, (key, value)
                -> value <= quantity ? null : value - quantity);
    }

    public synchronized void releaseReservation(String productId,
                                                int quantity,
                                                String orderId) {
        String reservationKey = productId + "#" + orderId;
        reservations.computeIfPresent(reservationKey, (key, value)
                -> value <= quantity ? null : value - quantity);
        reservations.computeIfPresent(productId, (key, value)
                -> value <= quantity ? null : value - quantity);
    }

    public int getAvailableQuantity(String productId) {
        return Math.max(0,
                quantities.getOrDefault(productId, 0)
                        - reservations.getOrDefault(productId, 0));
    }

    public Product getProduct(String productId) {
        return products.get(productId);
    }

    public Position getStoragePosition(String productId) {
        Product product = products.get(productId);
        return product == null ? null : product.getStoragePosition();
    }

    public Map<String, Integer> getAvailableInventorySnapshot() {
        Map<String, Integer> snapshot = new LinkedHashMap<>();
        for (String productId : products.keySet()) {
            snapshot.put(productId, getAvailableQuantity(productId));
        }
        return Collections.unmodifiableMap(snapshot);
    }
}
