package com.warehouse.ui;

import com.warehouse.environment.Cell;
import com.warehouse.environment.Position;
import com.warehouse.environment.Warehouse;
import com.warehouse.environment.WarehouseGrid;
import com.warehouse.simulation.ScenarioManager;
import com.warehouse.simulation.SimulationEventBus;
import com.warehouse.simulation.SimulationManager;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WarehouseApplication extends Application {
    private static final Pattern POSITION_PATTERN =
            Pattern.compile("\\((-?\\d+),(-?\\d+)\\)");
    private final Canvas canvas = new Canvas(560, 560);
    private final Label status = new Label("Scenario: Basic Order Fulfillment");
    private final Label scenarioDescription = new Label();
    private final ListView<String> communicationLog = new ListView<>();
    private final Label[] dashboardLabels = new Label[17];
    private final TableView<AgentRow> agentTable = new TableView<>();
    private final ObservableList<AgentRow> agentRows = FXCollections.observableArrayList();
    private final Label robotDetails = new Label("Click a robot to inspect it");
    private final GridPane legendGrid = new GridPane();
    private final SimulationManager simulationManager = new SimulationManager();
    private volatile Warehouse currentWarehouse;
    private volatile String selectedRobotId;
    private int configuredRobotCount = 3;

    @Override
    public void start(Stage stage) {
        currentWarehouse = new ScenarioManager()
                .createScenario(ScenarioManager.Scenario.BASIC_ORDER);

        BorderPane root = new BorderPane();
        root.setCenter(createWarehouseView());
        root.setTop(status);
        BorderPane.setMargin(status, new Insets(8));
        root.setRight(createDashboard());
        root.setBottom(createControls());

        SimulationEventBus.subscribe(entry -> Platform.runLater(() -> {
            communicationLog.getItems().add(entry);
            if (communicationLog.getItems().size() > 400) {
                communicationLog.getItems().remove(0);
            }
        }));
        canvas.setOnMouseClicked(event -> selectRobotAt(event.getX(), event.getY()));
        refreshLegend(configuredRobotCount);
        render();

        AnimationTimer timer = new AnimationTimer() {
            private long lastRefresh;

            @Override
            public void handle(long now) {
                if (now - lastRefresh < 250_000_000L) {
                    return;
                }
                lastRefresh = now;
                refreshDashboard();
                render();
            }
        };
        timer.start();

        stage.setTitle("Multi-Agent Smart Warehouse");
        stage.setScene(new Scene(root, 1280, 1050));
        stage.show();
    }

    private VBox createWarehouseView() {
        VBox view = new VBox(4, canvas, new Label("Warehouse Legend"), legendGrid);
        view.setPadding(new Insets(8));
        return view;
    }

    private VBox createControls() {
        ComboBox<ScenarioOption> scenario = new ComboBox<>();
        scenario.getItems().addAll(
                new ScenarioOption(ScenarioManager.Scenario.BASIC_ORDER,
                        "Basic Order Fulfillment", 3, 5,
                        "Multiple robots cooperate through inventory checks, Contract Net negotiation, pickup, and delivery."),
                new ScenarioOption(ScenarioManager.Scenario.MULTI_ROBOT_NEGOTIATION,
                        "Intelligent Task Allocation", 4, 10,
                        "Robots negotiate tasks with FIPA Contract Net and are selected using estimated execution cost."),
                new ScenarioOption(ScenarioManager.Scenario.COLLISION_AVOIDANCE,
                        "Collision Avoidance", 4, 6,
                        "Robots request shared-cell reservations and wait for conflicting traffic to clear."),
                new ScenarioOption(ScenarioManager.Scenario.DEADLOCK_RECOVERY,
                        "Deadlock Recovery", 3, 3,
                        "Robots share constrained warehouse corridors while traffic coordination monitors conflicts."),
                new ScenarioOption(ScenarioManager.Scenario.DYNAMIC_OBSTACLE,
                        "Dynamic Obstacle Avoidance", 3, 5,
                        "Robots plan with A* and recalculate paths when a route is blocked."),
                new ScenarioOption(ScenarioManager.Scenario.BATTERY_MANAGEMENT,
                        "Battery Management", 4, 8,
                        "Robot battery levels affect task proposals and autonomous task acceptance."),
                new ScenarioOption(ScenarioManager.Scenario.FULL_WAREHOUSE_STRESS,
                        "Full Warehouse Stress Test", 6, 20,
                        "A larger order queue exercises inventory, negotiation, concurrent movement, and traffic management."));
        scenario.getSelectionModel().selectFirst();

        Spinner<Integer> robotCount = new Spinner<>(1, 6, 3);
        robotCount.setEditable(true);
        Spinner<Integer> orderCount = new Spinner<>(1, 20, 5);
        orderCount.setEditable(true);
        Slider speed = new Slider(25, 200, 100);
        speed.setShowTickLabels(true);
        speed.setShowTickMarks(true);
        updateScenarioDescription(scenario.getValue(), robotCount.getValue(),
                orderCount.getValue());
        scenarioDescription.setWrapText(true);
        scenario.setOnAction(event -> {
            ScenarioOption selected = scenario.getValue();
            robotCount.getValueFactory().setValue(selected.robots);
            orderCount.getValueFactory().setValue(selected.tasks);
            updateScenarioDescription(selected, selected.robots, selected.tasks);
            configuredRobotCount = selected.robots;
            refreshLegend(configuredRobotCount);
        });
        robotCount.valueProperty().addListener((observable, oldValue, newValue) -> {
            configuredRobotCount = newValue;
            refreshLegend(newValue);
            updateScenarioDescription(scenario.getValue(), newValue, orderCount.getValue());
        });
        orderCount.valueProperty().addListener((observable, oldValue, newValue) ->
                updateScenarioDescription(scenario.getValue(), robotCount.getValue(), newValue));

        Button start = new Button("Start Scenario");
        Button pause = new Button("Pause");
        Button reset = new Button("Reset");
        start.setOnAction(event -> {
            if ("PAUSED".equals(SimulationEventBus.getStatus())) {
                simulationManager.resume();
                status.setText("Simulation: RUNNING");
                return;
            }
            ScenarioOption selected = scenario.getValue();
            status.setText("Starting: " + selected.name);
            currentWarehouse = new ScenarioManager().createScenario(selected.scenario);
            selectedRobotId = null;
            communicationLog.getItems().clear();
            configuredRobotCount = robotCount.getValue();
            refreshLegend(configuredRobotCount);
            int robots = robotCount.getValue();
            int orders = orderCount.getValue();
            int speedPercent = (int) speed.getValue();
            Thread startThread = new Thread(() -> {
                try {
                    simulationManager.start(selected.scenario, robots, orders, speedPercent);
                    Platform.runLater(() -> currentWarehouse = simulationManager.getWarehouse());
                } catch (Exception exception) {
                    Platform.runLater(() -> {
                        status.setText("Simulation failed to start");
                        communicationLog.getItems().add(
                                "[SYSTEM] ERROR: " + exception.getMessage());
                    });
                }
            }, "warehouse-scenario-start");
            startThread.setDaemon(true);
            startThread.start();
        });
        pause.setOnAction(event -> {
            simulationManager.pause();
            status.setText("Simulation: PAUSED (Start Scenario resumes)");
        });
        reset.setOnAction(event -> {
            Thread resetThread = new Thread(() -> {
                try {
                    simulationManager.reset();
                    Platform.runLater(() -> {
                        currentWarehouse = new ScenarioManager()
                                .createScenario(ScenarioManager.Scenario.BASIC_ORDER);
                        selectedRobotId = null;
                        status.setText("Simulation: RESET");
                        agentRows.clear();
                        communicationLog.getItems().clear();
                        render();
                    });
                } catch (Exception exception) {
                    Platform.runLater(() -> communicationLog.getItems()
                            .add("[SYSTEM] RESET ERROR: " + exception.getMessage()));
                }
            }, "warehouse-reset");
            resetThread.setDaemon(true);
            resetThread.start();
        });

        HBox controls = new HBox(8,
                new Label("Scenario:"), scenario,
                new Label("Robots:"), robotCount,
                new Label("Orders/Tasks:"), orderCount,
                new Label("Speed:"), speed,
                start, pause, reset);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.setPadding(new Insets(6));
        VBox bottom = new VBox(4, scenarioDescription, controls, communicationLog);
        communicationLog.setPrefHeight(140);
        bottom.setPadding(new Insets(8));
        return bottom;
    }

    private void updateScenarioDescription(ScenarioOption option, int robots, int tasks) {
        scenarioDescription.setText(option.name + " | Robots: " + robots
                + " | Tasks: " + tasks + "\nObjectives: " + option.objectives);
    }

    private VBox createDashboard() {
        String[] names = {
                "Total robots", "Active robots", "Idle robots", "Waiting robots",
                "Charging robots", "Pending orders", "Completed orders", "Failed orders",
                "Pending tasks", "Active tasks", "Completed tasks", "Total tasks",
                "Average completion time", "Robot utilization", "Negotiations",
                "Detected conflicts", "Resolved deadlocks"
        };
        VBox box = new VBox(5, new Label("Dashboard"));
        for (int i = 0; i < names.length; i++) {
            dashboardLabels[i] = new Label(names[i] + ": 0");
            box.getChildren().add(dashboardLabels[i]);
        }

        TableColumn<AgentRow, String> agent = new TableColumn<>("Agent");
        agent.setCellValueFactory(value -> value.getValue().agent);
        TableColumn<AgentRow, String> type = new TableColumn<>("Type");
        type.setCellValueFactory(value -> value.getValue().type);
        TableColumn<AgentRow, String> state = new TableColumn<>("State");
        state.setCellValueFactory(value -> value.getValue().state);
        TableColumn<AgentRow, String> activity = new TableColumn<>("Current Activity");
        activity.setCellValueFactory(value -> value.getValue().activity);
        agentTable.getColumns().setAll(agent, type, state, activity);
        agentTable.setItems(agentRows);
        agentTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        agentTable.setPrefWidth(390);
        agentTable.setPrefHeight(260);

        box.getChildren().addAll(new Separator(),
                new Label("Agent Monitor"), agentTable,
                new Separator(), robotDetails);
        box.setPadding(new Insets(8));
        box.setPrefWidth(410);
        return box;
    }

    private void refreshDashboard() {
        Map<String, String> robots = SimulationEventBus.getRobotStates();
        long active = robots.values().stream().filter(value ->
                statusOf(value).matches("MOVING|DELIVERING|PICKING_UP|NEGOTIATING")).count();
        long idle = robots.values().stream().filter(value -> statusOf(value).equals("IDLE")).count();
        long waiting = robots.values().stream().filter(value -> statusOf(value).equals("WAITING")).count();
        long charging = robots.values().stream().filter(value -> statusOf(value).equals("CHARGING")).count();
        String[] values = {
                String.valueOf(robots.size()), String.valueOf(active), String.valueOf(idle),
                String.valueOf(waiting), String.valueOf(charging),
                String.valueOf(SimulationEventBus.getPendingOrders()),
                String.valueOf(SimulationEventBus.getCompletedOrders()),
                String.valueOf(SimulationEventBus.getFailedOrders()),
                String.valueOf(SimulationEventBus.getPendingTaskCount()),
                String.valueOf(SimulationEventBus.getActiveTasks()),
                String.valueOf(SimulationEventBus.getCompletedTaskCount()),
                String.valueOf(SimulationEventBus.getTotalOrders()),
                String.format("%.2f s", SimulationEventBus.getAverageCompletionSeconds()),
                String.format("%.1f%%", SimulationEventBus.getRobotUtilizationPercent()),
                String.valueOf(SimulationEventBus.getNegotiations()),
                String.valueOf(SimulationEventBus.getCollisions()),
                String.valueOf(SimulationEventBus.getDeadlocksResolved())
        };
        String[] names = {
                "Total robots", "Active robots", "Idle robots", "Waiting robots",
                "Charging robots", "Pending orders", "Completed orders", "Failed orders",
                "Pending tasks", "Active tasks", "Completed tasks", "Total tasks",
                "Average completion time", "Robot utilization", "Negotiations",
                "Detected conflicts", "Resolved deadlocks"
        };
        for (int i = 0; i < values.length; i++) {
            dashboardLabels[i].setText(names[i] + ": " + values[i]);
        }

        agentRows.clear();
        SimulationEventBus.getAgentStates().forEach((name, value) -> {
            String[] parts = value.split("\\|", -1);
            agentRows.add(new AgentRow(name, parts[0], parts[1], parts[2]));
        });
        robots.forEach((name, value) -> {
            String[] parts = robotParts(value);
            agentRows.add(new AgentRow(name, "Robot", parts[1],
                    parts[1] + " | " + parts[2] + " | battery " + parts[4]));
        });
        String simulationStatus = SimulationEventBus.getStatus();
        if ("COMPLETED".equals(simulationStatus)) {
            status.setText("SIMULATION COMPLETED");
        } else if ("RUNNING".equals(simulationStatus)) {
            status.setText("Simulation: RUNNING");
        }
        if (selectedRobotId != null && robots.containsKey(selectedRobotId)) {
            updateRobotDetails(selectedRobotId, robotParts(robots.get(selectedRobotId)));
        }
    }

    private String statusOf(String snapshot) {
        return robotParts(snapshot)[1];
    }

    private String[] robotParts(String snapshot) {
        String[] parts = snapshot.split("\\|", -1);
        if (parts.length >= 8) {
            return parts;
        }
        String[] extended = new String[8];
        System.arraycopy(parts, 0, extended, 0, parts.length);
        for (int i = parts.length; i < extended.length; i++) {
            extended[i] = "-";
        }
        return extended;
    }

    private void render() {
        Warehouse warehouse = currentWarehouse;
        if (warehouse == null) {
            return;
        }
        WarehouseGrid grid = warehouse.getGrid();
        GraphicsContext graphics = canvas.getGraphicsContext2D();
        double cellWidth = canvas.getWidth() / grid.getWidth();
        double cellHeight = canvas.getHeight() / grid.getHeight();
        graphics.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        for (int x = 0; x < grid.getWidth(); x++) {
            for (int y = 0; y < grid.getHeight(); y++) {
                Position position = new Position(x, y);
                Cell cell = grid.getCell(position);
                graphics.setFill(WarehouseVisuals.cellColor(
                        cell.getType(), cell.isTemporarilyBlocked()));
                graphics.fillRect(x * cellWidth, y * cellHeight,
                        cellWidth - 1, cellHeight - 1);
            }
        }
        drawReservations(graphics, cellWidth, cellHeight);
        drawRobots(graphics, cellWidth, cellHeight);
    }

    private void drawReservations(GraphicsContext graphics,
                                  double cellWidth, double cellHeight) {
        SimulationEventBus.getCellReservations().forEach((cell, robotId) -> {
            int[] xy = parseCoordinate(cell);
            graphics.setStroke(Color.YELLOW);
            graphics.setLineWidth(3);
            graphics.strokeRect(xy[0] * cellWidth + 2, xy[1] * cellHeight + 2,
                    cellWidth - 5, cellHeight - 5);
        });
    }

    private void drawRobots(GraphicsContext graphics, double cellWidth, double cellHeight) {
        Map<String, String> robots = SimulationEventBus.getRobotStates();
        robots.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String[] parts = robotParts(entry.getValue());
            int[] current = parseCoordinate(parts[0]);
            Color robotColor = WarehouseVisuals.robotColor(entry.getKey());
            drawPath(graphics, parts[7], current, robotColor, cellWidth, cellHeight);
            drawDestination(graphics, parts[3], cellWidth, cellHeight);

            double centerX = (current[0] + 0.5) * cellWidth;
            double centerY = (current[1] + 0.5) * cellHeight;
            graphics.setFill(robotColor);
            graphics.fillOval(current[0] * cellWidth + 4, current[1] * cellHeight + 4,
                    cellWidth - 8, cellHeight - 8);
            graphics.setFill(Color.WHITE);
            String id = entry.getKey().replaceAll("\\D+", "");
            graphics.fillText(id, centerX - 5, centerY + 3);
            String marker = statusMarker(parts[1]);
            graphics.fillText(marker, centerX + 6, centerY + 10);
            if ("true".equalsIgnoreCase(parts[6])) {
                graphics.setFill(Color.WHITE);
                graphics.fillRect(centerX - 3, centerY - 12, 7, 7);
                graphics.setStroke(Color.BLACK);
                graphics.strokeRect(centerX - 3, centerY - 12, 7, 7);
            }
            if (entry.getKey().equals(selectedRobotId)) {
                graphics.setStroke(Color.WHITE);
                graphics.setLineWidth(2);
                graphics.strokeOval(current[0] * cellWidth + 1,
                        current[1] * cellHeight + 1, cellWidth - 2, cellHeight - 2);
                updateRobotDetails(entry.getKey(), parts);
            }
        });
    }

    private void drawPath(GraphicsContext graphics, String path, int[] current,
                          Color color, double cellWidth, double cellHeight) {
        Matcher matcher = POSITION_PATTERN.matcher(path);
        int previousX = current[0];
        int previousY = current[1];
        graphics.setStroke(color);
        graphics.setLineWidth(3);
        while (matcher.find()) {
            int x = Integer.parseInt(matcher.group(1));
            int y = Integer.parseInt(matcher.group(2));
            graphics.strokeLine((previousX + 0.5) * cellWidth,
                    (previousY + 0.5) * cellHeight,
                    (x + 0.5) * cellWidth, (y + 0.5) * cellHeight);
            previousX = x;
            previousY = y;
        }
    }

    private void drawDestination(GraphicsContext graphics, String destination,
                                 double cellWidth, double cellHeight) {
        Matcher matcher = POSITION_PATTERN.matcher(destination);
        if (!matcher.find()) {
            return;
        }
        int x = Integer.parseInt(matcher.group(1));
        int y = Integer.parseInt(matcher.group(2));
        graphics.setStroke(Color.YELLOW);
        graphics.setLineWidth(2);
        graphics.strokeOval(x * cellWidth + 4, y * cellHeight + 4,
                cellWidth - 8, cellHeight - 8);
    }

    private String statusMarker(String robotStatus) {
        return switch (robotStatus) {
            case "IDLE" -> "I";
            case "MOVING" -> ">";
            case "WAITING" -> "!";
            case "CHARGING" -> "C";
            case "NEGOTIATING" -> "N";
            case "PICKING_UP" -> "P";
            case "DELIVERING" -> "D";
            case "BLOCKED" -> "X";
            default -> "?";
        };
    }

    private int[] parseCoordinate(String value) {
        Matcher matcher = POSITION_PATTERN.matcher(value);
        if (matcher.find()) {
            return new int[]{Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2))};
        }
        String[] xy = value.split(",");
        if (xy.length == 2) {
            return new int[]{Integer.parseInt(xy[0]), Integer.parseInt(xy[1])};
        }
        return new int[]{0, 0};
    }

    private void selectRobotAt(double pixelX, double pixelY) {
        if (currentWarehouse == null) {
            return;
        }
        double cellWidth = canvas.getWidth() / currentWarehouse.getGrid().getWidth();
        double cellHeight = canvas.getHeight() / currentWarehouse.getGrid().getHeight();
        String coordinate = (int) (pixelX / cellWidth) + ","
                + (int) (pixelY / cellHeight);
        selectedRobotId = SimulationEventBus.getRobotStates().entrySet().stream()
                .filter(entry -> robotParts(entry.getValue())[0].equals(coordinate))
                .map(Map.Entry::getKey).findFirst().orElse(null);
        if (selectedRobotId != null) {
            updateRobotDetails(selectedRobotId,
                    robotParts(SimulationEventBus.getRobotStates().get(selectedRobotId)));
        }
        render();
    }

    private void updateRobotDetails(String robotId, String[] parts) {
        robotDetails.setText(robotId + " | Position " + parts[0]
                + " | State " + parts[1] + " | Task " + parts[2]
                + " | Destination " + parts[3] + " | Battery " + parts[4]
                + " | Completed " + parts[5] + " | Carrying " + parts[6]
                + " | Path " + parts[7]);
    }

    private void refreshLegend(int robotCount) {
        legendGrid.getChildren().clear();
        legendGrid.setHgap(8);
        legendGrid.setVgap(3);
        List<WarehouseVisuals.LegendEntry> entries = WarehouseVisuals.legend(robotCount);
        for (int i = 0; i < entries.size(); i++) {
            WarehouseVisuals.LegendEntry entry = entries.get(i);
            HBox item = new HBox(4, createLegendSwatch(entry), new Label(entry.label()));
            item.setAlignment(Pos.CENTER_LEFT);
            GridPane.setHgrow(item, Priority.ALWAYS);
            legendGrid.add(item, i % 4, i / 4);
        }
    }

    private StackPane createLegendSwatch(WarehouseVisuals.LegendEntry entry) {
        StackPane swatch = new StackPane();
        swatch.setMinSize(22, 18);
        swatch.setPrefSize(22, 18);
        if (entry.marker().startsWith("state:")) {
            swatch.setStyle("-fx-background-color: "
                    + toCss(WarehouseVisuals.robotColor("ROBOT-01"))
                    + "; -fx-border-color: #777;");
            Label marker = new Label(entry.marker().substring(6));
            marker.setTextFill(Color.WHITE);
            swatch.getChildren().add(marker);
        } else if ("border".equals(entry.marker())) {
            swatch.setStyle("-fx-background-color: lightgray; -fx-border-color: yellow; -fx-border-width: 3;");
        } else if ("ring".equals(entry.marker())) {
            Circle ring = new Circle(7, Color.TRANSPARENT);
            ring.setStroke(entry.color());
            ring.setStrokeWidth(2);
            swatch.getChildren().add(ring);
        } else if ("line".equals(entry.marker())) {
            Region line = new Region();
            line.setPrefHeight(3);
            line.setMaxWidth(Double.MAX_VALUE);
            line.setStyle("-fx-background-color: " + toCss(entry.color()));
            swatch.getChildren().add(line);
        } else if ("robot-line".equals(entry.marker())) {
            swatch.setStyle("-fx-background-color: lightgray;");
            Circle robot = new Circle(6, entry.color());
            Region line = new Region();
            line.setPrefHeight(3);
            line.setMaxWidth(12);
            line.setStyle("-fx-background-color: " + toCss(entry.color()));
            HBox symbols = new HBox(1, robot, line);
            symbols.setAlignment(Pos.CENTER);
            swatch.getChildren().add(symbols);
        } else if ("box".equals(entry.marker())) {
            swatch.setStyle("-fx-background-color: #3f3f3f;");
            Region box = new Region();
            box.setPrefSize(7, 7);
            box.setStyle("-fx-background-color: white; -fx-border-color: black;");
            swatch.getChildren().add(box);
        } else if ("robot".equals(entry.marker())) {
            Circle robot = new Circle(7, entry.color());
            swatch.getChildren().add(robot);
        } else {
            swatch.setStyle("-fx-background-color: " + toCss(entry.color())
                    + "; -fx-border-color: #777;");
        }
        return swatch;
    }

    private String toCss(Color color) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255));
    }

    private record ScenarioOption(ScenarioManager.Scenario scenario, String name,
                                  int robots, int tasks, String objectives) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static class AgentRow {
        private final SimpleStringProperty agent;
        private final SimpleStringProperty type;
        private final SimpleStringProperty state;
        private final SimpleStringProperty activity;

        private AgentRow(String agent, String type, String state, String activity) {
            this.agent = new SimpleStringProperty(agent);
            this.type = new SimpleStringProperty(type);
            this.state = new SimpleStringProperty(state);
            this.activity = new SimpleStringProperty(activity);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
