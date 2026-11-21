# Multi-Agent Smart Warehouse Management System

## Prerequisites

- Java 17 or later
- Maven 3.9 or later

## Build

```powershell
mvn clean test
```

## Run Phase 2 JADE bootstrap

```powershell
mvn compile exec:java "-Dexec.mainClass=com.warehouse.Main"
```

The bootstrap creates the main JADE container, launches the warehouse agents,
registers them in the Directory Facilitator, and verifies inventory discovery
and ACL communication through `CommunicationProbeAgent`.

## Run JavaFX visualization

```powershell
mvn compile exec:java "-Dexec.mainClass=com.warehouse.ui.WarehouseApplication"
```

The visualization provides scenario selection, robot and order controls,
live agent and robot monitoring, communication logs, a synchronized warehouse
legend, and a reset/pause/resume lifecycle. Orders are queued and negotiated
continuously through JADE ACL messages; robots plan with A* and request traffic
reservations as they move. Scenario defaults range from 3 robots / 5 orders
for basic fulfillment to 6 robots / 20 orders for the stress test.
