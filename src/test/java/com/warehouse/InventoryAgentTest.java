package com.warehouse;

import com.warehouse.environment.Position;
import com.warehouse.models.Product;
import com.warehouse.simulation.StockInventory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryAgentTest {
    @Test
    void reservesStockOnlyOnce() {
        StockInventory inventory = new StockInventory();
        inventory.addStock(new Product("P1", "Product A", new Position(2, 2)), 5);

        assertTrue(inventory.reserve("P1", 3, "ORDER-01"));
        assertEquals(2, inventory.getAvailableQuantity("P1"));
        assertFalse(inventory.reserve("P1", 3, "ORDER-02"));
    }

    @Test
    void releaseRestoresReservedStock() {
        StockInventory inventory = new StockInventory();
        inventory.addStock(new Product("P1", "Product A", new Position(2, 2)), 2);

        assertTrue(inventory.reserve("P1", 2, "ORDER-01"));
        inventory.releaseReservation("P1", 2, "ORDER-01");

        assertEquals(2, inventory.getAvailableQuantity("P1"));
    }

    @Test
    void commitRemovesStockAfterRetrieval() {
        StockInventory inventory = new StockInventory();
        inventory.addStock(new Product("P1", "Product A", new Position(2, 2)), 4);

        inventory.reserve("P1", 4, "ORDER-01");
        inventory.commitReservation("P1", 4, "ORDER-01");

        assertEquals(0, inventory.getAvailableQuantity("P1"));
    }
}
