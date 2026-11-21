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
live agent and robot monitoring, communication logs, and a reset/pause/start
control lifecycle. Scenario execution uses JADE agents, DF discovery, ACL
contract-net negotiation, A* path planning, and traffic reservations.
