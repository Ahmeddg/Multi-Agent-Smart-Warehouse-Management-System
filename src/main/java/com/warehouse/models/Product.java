package com.warehouse.models;

import com.warehouse.environment.Position;

import java.util.Objects;

public final class Product {
    private final String id;
    private final String name;
    private final Position storagePosition;

    public Product(String id, String name, Position storagePosition) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.storagePosition = Objects.requireNonNull(storagePosition);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Position getStoragePosition() {
        return storagePosition;
    }
}
