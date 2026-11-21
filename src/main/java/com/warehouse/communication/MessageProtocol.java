package com.warehouse.communication;

public final class MessageProtocol {
    public static final String INVENTORY_SERVICE = "inventory-management";
    public static final String ORDER_SERVICE = "order-management";
    public static final String ALLOCATION_SERVICE = "task-allocation";
    public static final String TRAFFIC_SERVICE = "traffic-management";
    public static final String MONITORING_SERVICE = "warehouse-monitoring";
    public static final String ROBOT_SERVICE = "robot-transport";

    public static final String STOCK_REQUEST = "STOCK_REQUEST";
    public static final String RESERVE_REQUEST = "RESERVE_REQUEST";
    public static final String TRANSPORT_TASK = "TRANSPORT_TASK";
    public static final String MOVEMENT_REQUEST = "MOVEMENT_REQUEST";
    public static final String STATUS_REQUEST = "STATUS_REQUEST";

    private MessageProtocol() {
    }
}
