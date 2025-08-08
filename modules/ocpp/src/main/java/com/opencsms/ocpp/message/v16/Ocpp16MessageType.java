package com.opencsms.ocpp.message.v16;

/**
 * OCPP 1.6 message action types.
 */
public enum Ocpp16MessageType {
    
    // Core Profile - CP to CS
    AUTHORIZE("Authorize"),
    BOOT_NOTIFICATION("BootNotification"),
    DATA_TRANSFER("DataTransfer"),
    HEARTBEAT("Heartbeat"),
    METER_VALUES("MeterValues"),
    START_TRANSACTION("StartTransaction"),
    STATUS_NOTIFICATION("StatusNotification"),
    STOP_TRANSACTION("StopTransaction"),
    
    // Core Profile - CS to CP
    CANCEL_RESERVATION("CancelReservation"),
    CHANGE_AVAILABILITY("ChangeAvailability"),
    CHANGE_CONFIGURATION("ChangeConfiguration"),
    CLEAR_CACHE("ClearCache"),
    GET_CONFIGURATION("GetConfiguration"),
    REMOTE_START_TRANSACTION("RemoteStartTransaction"),
    REMOTE_STOP_TRANSACTION("RemoteStopTransaction"),
    RESERVE_NOW("ReserveNow"),
    RESET("Reset"),
    UNLOCK_CONNECTOR("UnlockConnector"),
    
    // Firmware Management Profile
    DIAGNOSTICS_STATUS_NOTIFICATION("DiagnosticsStatusNotification"),
    FIRMWARE_STATUS_NOTIFICATION("FirmwareStatusNotification"),
    GET_DIAGNOSTICS("GetDiagnostics"),
    UPDATE_FIRMWARE("UpdateFirmware"),
    
    // Local Auth List Management Profile
    GET_LOCAL_LIST_VERSION("GetLocalListVersion"),
    SEND_LOCAL_LIST("SendLocalList"),
    
    // Reservation Profile
    // (Already covered in core profile)
    
    // Smart Charging Profile
    CLEAR_CHARGING_PROFILE("ClearChargingProfile"),
    GET_COMPOSITE_SCHEDULE("GetCompositeSchedule"),
    SET_CHARGING_PROFILE("SetChargingProfile"),
    
    // Remote Trigger Profile
    TRIGGER_MESSAGE("TriggerMessage");
    
    private final String action;
    
    Ocpp16MessageType(String action) {
        this.action = action;
    }
    
    public String getAction() {
        return action;
    }
    
    public static Ocpp16MessageType fromAction(String action) {
        for (Ocpp16MessageType type : values()) {
            if (type.action.equals(action)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown OCPP 1.6 action: " + action);
    }
    
    @Override
    public String toString() {
        return action;
    }
}