package io.github.lzdev42.catalyticui.i18n

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Application strings data class for multi-language support.
 * 
 * All field names use snake_case to match JSON keys.
 * Default values are English strings (fallback).
 * 
 * Supported languages:
 * - en: English (default, development language)
 * - zh_CN: Simplified Chinese (简体中文)
 * - zh_TW: Traditional Chinese (繁體中文)
 */
@Serializable
data class AppStrings(
    // ===== App & Header =====
    @SerialName("app_title")
    val appTitle: String = "Catalytic",
    
    @SerialName("select_working_directory")
    val selectWorkingDirectory: String = "Select Working Directory",
    
    @SerialName("header_start_all")
    val headerStartAll: String = "Start All",
    
    @SerialName("header_stop")
    val headerStop: String = "Stop",
    
    @SerialName("header_settings")
    val headerSettings: String = "Settings",
    
    @SerialName("test_steps")
    val testSteps: String = "Test Steps",
    
    @SerialName("language")
    val language: String = "Language",
    
    @SerialName("settings")
    val settings: String = "Settings",
    
    @SerialName("system_logs")
    val systemLogs: String = "System Logs",
    
    @SerialName("no_test_steps")
    val noTestSteps: String = "No test steps",
    
    @SerialName("no_logs")
    val noLogs: String = "No logs",
    
    @SerialName("no_slots_data")
    val noSlotsData: String = "No slot data available",
    
    @SerialName("connect_host_prompt")
    val connectHostPrompt: String = "Please connect to Host or load test data",
    
    // ===== Slot Status =====
    @SerialName("slot_idle")
    val slotIdle: String = "Idle",
    
    @SerialName("slot_running")
    val slotRunning: String = "Running",
    
    @SerialName("slot_paused")
    val slotPaused: String = "Paused",
    
    @SerialName("slot_pass")
    val slotPass: String = "PASS",
    
    @SerialName("slot_fail")
    val slotFail: String = "FAIL",
    
    @SerialName("slot_no_sn")
    val slotNoSn: String = "No SN scanned",
    
    @SerialName("slot_wait_scan")
    val slotWaitScan: String = "Waiting for scan",
    
    // ===== Device Management =====
    @SerialName("device_management_title")
    val deviceManagementTitle: String = "Device Management",
    
    @SerialName("device_management_subtitle")
    val deviceManagementSubtitle: String = "Manage device types and instances",
    
    @SerialName("device_add_type_button")
    val deviceAddTypeButton: String = "+ Add Device Type",
    
    @SerialName("device_add_type_dialog_title")
    val deviceAddTypeDialogTitle: String = "Add Device Type",
    
    @SerialName("device_type_name")
    val deviceTypeName: String = "Type Name",
    
    @SerialName("device_transport")
    val deviceTransport: String = "Transport",
    
    @SerialName("device_plugin")
    val devicePlugin: String = "Communication Plugin",
    
    @SerialName("device_select_plugin")
    val deviceSelectPlugin: String = "Select Plugin...",
    
    @SerialName("device_transport_serial")
    val deviceTransportSerial: String = "Serial",
    
    @SerialName("device_transport_tcp")
    val deviceTransportTcp: String = "TCP/IP",
    
    @SerialName("device_transport_usb")
    val deviceTransportUsb: String = "USB",
    
    @SerialName("device_no_types")
    val deviceNoTypes: String = "No device types",
    
    @SerialName("device_click_to_add")
    val deviceClickToAdd: String = "Click below to add device type",
    
    @SerialName("device_add_device_to")
    val deviceAddDeviceTo: String = "Add Device to %s",
    
    @SerialName("device_name")
    val deviceName: String = "Device Name",
    
    @SerialName("device_address")
    val deviceAddress: String = "Device Address",
    
    @SerialName("device_placeholder_name")
    val devicePlaceholderName: String = "e.g. DMM-001",
    
    @SerialName("device_placeholder_address")
    val devicePlaceholderAddress: String = "e.g. COM3 or 192.168.1.100",
    
    @SerialName("device_add_command_to")
    val deviceAddCommandTo: String = "Add Command to %s",
    
    @SerialName("device_command_name")
    val deviceCommandName: String = "Command Name",
    
    @SerialName("device_command_payload")
    val deviceCommandPayload: String = "Command Payload",
    
    @SerialName("device_command_parse_rule")
    val deviceCommandParseRule: String = "Parse Rule (Optional)",
    
    @SerialName("device_placeholder_command_name")
    val devicePlaceholderCommandName: String = "e.g. Read Voltage",
    
    @SerialName("device_placeholder_payload")
    val devicePlaceholderPayload: String = "e.g. MEAS:VOLT:DC?",
    
    @SerialName("device_placeholder_parse_rule")
    val devicePlaceholderParseRule: String = "Regex or rule name",
    
    @SerialName("device_instances")
    val deviceInstances: String = "Device Instances (%d)",
    
    @SerialName("device_no_instances")
    val deviceNoInstances: String = "No device instances",
    
    @SerialName("device_commands")
    val deviceCommands: String = "Supported Commands (%d)",
    
    @SerialName("device_no_commands")
    val deviceNoCommands: String = "No commands defined",
    
    @SerialName("device_count_suffix")
    val deviceCountSuffix: String = "%d units",
    
    @SerialName("device_online")
    val deviceOnline: String = "Online",
    
    @SerialName("device_offline")
    val deviceOffline: String = "Offline",
    
    @SerialName("device_test")
    val deviceTest: String = "Test",
    
    @SerialName("device_edit")
    val deviceEdit: String = "Edit",
    
    @SerialName("device_parse_prefix")
    val deviceParsePrefix: String = "Parse: %s",
    
    // ===== Flow Definition =====
    @SerialName("flow_title")
    val flowTitle: String = "Test Flow",
    
    @SerialName("flow_subtitle")
    val flowSubtitle: String = "Define test steps and logic",
    
    @SerialName("flow_no_steps")
    val flowNoSteps: String = "No test steps",
    
    @SerialName("flow_click_to_add")
    val flowClickToAdd: String = "Click below to add step",
    
    @SerialName("flow_add_step")
    val flowAddStep: String = "+ Add Step",
    
    @SerialName("flow_mode_engine")
    val flowModeEngine: String = "Engine",
    
    @SerialName("flow_mode_host")
    val flowModeHost: String = "Host",
    
    @SerialName("flow_mode_calculation")
    val flowModeCalculation: String = "Calculation",
    
    @SerialName("flow_step_name")
    val flowStepName: String = "Name",
    
    @SerialName("flow_step_mode")
    val flowStepMode: String = "Mode",
    
    @SerialName("flow_device_and_command")
    val flowDeviceAndCommand: String = "Device & Command",
    
    @SerialName("flow_device_type")
    val flowDeviceType: String = "Device Type",
    
    @SerialName("flow_select_device_type")
    val flowSelectDeviceType: String = "Select device type...",
    
    @SerialName("flow_execute_command")
    val flowExecuteCommand: String = "Execute Command",
    
    @SerialName("flow_select_command")
    val flowSelectCommand: String = "Select command...",
    
    @SerialName("flow_select_device_first")
    val flowSelectDeviceFirst: String = "Select device type first",
    
    @SerialName("flow_loop_config")
    val flowLoopConfig: String = "Loop Configuration (Optional)",
    
    @SerialName("flow_loop_max_iterations")
    val flowLoopMaxIterations: String = "Max Iterations",
    
    @SerialName("flow_loop_delay_ms")
    val flowLoopDelayMs: String = "Delay (ms)",
    
    @SerialName("flow_loop_placeholder")
    val flowLoopPlaceholder: String = "Empty = execute once",
    
    @SerialName("flow_break_condition")
    val flowBreakCondition: String = "Break Condition",
    
    @SerialName("flow_break_placeholder")
    val flowBreakPlaceholder: String = "e.g. voltage_a > 3.3",
    
    @SerialName("flow_host_task_config")
    val flowHostTaskConfig: String = "Host Task Configuration",
    
    @SerialName("flow_task_name")
    val flowTaskName: String = "Task Name",
    
    @SerialName("flow_task_placeholder")
    val flowTaskPlaceholder: String = "e.g. WaitDeviceReady",
    
    @SerialName("flow_timeout_ms")
    val flowTimeoutMs: String = "Timeout (ms)",
    
    @SerialName("flow_params_json")
    val flowParamsJson: String = "Parameters (JSON)",
    
    @SerialName("flow_variables")
    val flowVariables: String = "Variables",
    
    @SerialName("flow_variable_name")
    val flowVariableName: String = "Variable Name",
    
    @SerialName("flow_add_variable")
    val flowAddVariable: String = "+ Add Variable",
    
    @SerialName("flow_check_rule")
    val flowCheckRule: String = "Check Rule",
    
    @SerialName("flow_expression_dev")
    val flowExpressionDev: String = "Expression (In Development)",
    
    @SerialName("flow_dev_placeholder")
    val flowDevPlaceholder: String = "In development...",
    
    @SerialName("flow_calculation_config")
    val flowCalculationConfig: String = "Calculation Configuration",
    
    @SerialName("flow_calculation_desc")
    val flowCalculationDesc: String = "This step does not send commands, only performs calculations on variables from previous steps.",
    
    @SerialName("flow_select_input_vars")
    val flowSelectInputVars: String = "Select Input Variables (from previous steps)",
    
    @SerialName("flow_no_available_vars")
    val flowNoAvailableVars: String = "No available variables (define variables in previous steps first)",
    
    @SerialName("flow_selected_prefix")
    val flowSelectedPrefix: String = "Selected: %s",
    
    // ===== Slot Binding =====
    @SerialName("slot_binding_title")
    val slotBindingTitle: String = "Slot Binding",
    
    @SerialName("slot_binding_subtitle")
    val slotBindingSubtitle: String = "Configure device instances for each slot (multi-device support)",
    
    @SerialName("slot_count")
    val slotCount: String = "Slot Count",
    
    @SerialName("slot_label")
    val slotLabel: String = "Slot %d",
    
    @SerialName("slot_no_devices")
    val slotNoDevices: String = "No devices bound",
    
    @SerialName("slot_bind_dialog_title")
    val slotBindDialogTitle: String = "Bind Device to Slot %d",
    
    @SerialName("slot_select_device_instance")
    val slotSelectDeviceInstance: String = "Select Device Instance",
    
    @SerialName("slot_no_devices_in_type")
    val slotNoDevicesInType: String = "No devices available under this type, please add them in Device Management first",
    

    
    // ===== Status Bar =====
    @SerialName("status_flow_label")
    val statusFlowLabel: String = "Flow",
    
    @SerialName("status_hide_logs")
    val statusHideLogs: String = "Hide Logs",
    
    @SerialName("status_show_logs")
    val statusShowLogs: String = "Show Logs",
    
    @SerialName("status_today_stats")
    val statusTodayStats: String = "Today",
    
    // ===== Connection Tab =====
    @SerialName("connection_title")
    val connectionTitle: String = "Connection",
    
    @SerialName("connection_subtitle")
    val connectionSubtitle: String = "gRPC connection settings",
    
    @SerialName("connection_connected")
    val connectionConnected: String = "Connected",
    
    @SerialName("connection_disconnected")
    val connectionDisconnected: String = "Disconnected",
    
    @SerialName("connection_reconnecting")
    val connectionReconnecting: String = "Reconnecting...",
    
    // ===== Connection Panel =====
    @SerialName("panel_device_connections")
    val panelDeviceConnections: String = "Device Connections",
    
    @SerialName("panel_connect_host_first")
    val panelConnectHostFirst: String = "Please connect to Host first",
    
    @SerialName("panel_no_devices_configured")
    val panelNoDevicesConfigured: String = "No devices configured",
    
    @SerialName("panel_connected_format")
    val panelConnectedFormat: String = "%d / %d Connected",
    
    @SerialName("status_connecting")
    val statusConnecting: String = "Connecting...",
    
    @SerialName("status_error")
    val statusError: String = "Error",
    
    @SerialName("action_connect")
    val actionConnect: String = "Connect",
    
    @SerialName("action_disconnect")
    val actionDisconnect: String = "Disconnect",

    // ===== Appearance Tab =====
    @SerialName("appearance_title")
    val appearanceTitle: String = "Appearance",
    
    @SerialName("appearance_subtitle")
    val appearanceSubtitle: String = "Theme and language settings",
    
    @SerialName("appearance_theme")
    val appearanceTheme: String = "Theme",
    
    @SerialName("appearance_theme_dark")
    val appearanceThemeDark: String = "Dark",
    
    @SerialName("appearance_theme_light")
    val appearanceThemeLight: String = "Light",
    
    @SerialName("appearance_language")
    val appearanceLanguage: String = "Language",
    
    @SerialName("appearance_language_zh")
    val appearanceLanguageZh: String = "Chinese",
    
    @SerialName("appearance_language_en")
    val appearanceLanguageEn: String = "English",
    
    // ===== About Tab =====
    @SerialName("about_title")
    val aboutTitle: String = "About",
    
    @SerialName("about_subtitle")
    val aboutSubtitle: String = "Application information",
    
    // ===== Common Actions =====
    @SerialName("common_save")
    val commonSave: String = "Save",
    
    @SerialName("common_cancel")
    val commonCancel: String = "Cancel",
    
    @SerialName("common_ok")
    val commonOk: String = "OK",
    
    @SerialName("common_add")
    val commonAdd: String = "Add",
    
    @SerialName("common_edit")
    val commonEdit: String = "Edit",
    
    @SerialName("common_delete")
    val commonDelete: String = "Delete",
    
    @SerialName("common_test")
    val commonTest: String = "Test",
    
    @SerialName("common_browse")
    val commonBrowse: String = "Browse",
    
    @SerialName("common_confirm")
    val commonConfirm: String = "Confirm",
    
    // ===== Validation Alerts =====
    @SerialName("alert_cannot_start")
    val alertCannotStart: String = "Cannot Start Test",
    
    @SerialName("alert_no_devices")
    val alertNoDevices: String = "Please configure devices in Settings first",
    
    @SerialName("alert_no_steps")
    val alertNoSteps: String = "Please configure test flow in Settings first",
    
    @SerialName("alert_slot_no_sn")
    val alertSlotNoSn: String = "Slot %d has no SN",
    
    @SerialName("alert_all_slots_no_sn")
    val alertAllSlotsNoSn: String = "All idle slots have no SN",
    
    @SerialName("alert_no_ready_slots")
    val alertNoReadySlots: String = "No slots ready to start"
)
