package com.applocker.device;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final DeviceRegistry devices;

    public DeviceController(DeviceRegistry devices) {
        this.devices = devices;
    }

    @GetMapping
    public DevicesResponse list() {
        return new DevicesResponse(devices.list(), devices.managementRequests(), devices.lastManagementRequestAt());
    }
}
