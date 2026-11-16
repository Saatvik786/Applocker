package com.applocker.device;

import com.applocker.service.DeviceService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class DeviceRegistry {

    private final DeviceService records;
    private final ConcurrentHashMap<String, EnrolledDevice> devices = new ConcurrentHashMap<>();
    private final AtomicLong managementRequests = new AtomicLong();
    private volatile Instant lastManagementRequestAt;

    public DeviceRegistry(DeviceService records) {
        this.records = records;
    }

    public void save(EnrolledDevice device) {
        devices.put(device.windowsDeviceId(), device);
        records.upsert(device);
    }

    public EnrolledDevice find(String windowsDeviceId) {
        EnrolledDevice cached = devices.get(windowsDeviceId);
        if (cached != null) {
            return cached;
        }
        EnrolledDevice stored = records.find(windowsDeviceId);
        if (stored != null) {
            devices.put(windowsDeviceId, stored);
        }
        return stored;
    }

    public List<EnrolledDevice> list() {
        for (EnrolledDevice stored : records.list()) {
            devices.putIfAbsent(stored.windowsDeviceId(), stored);
        }
        return List.copyOf(devices.values());
    }

    public void recordManagement(Instant at) {
        managementRequests.incrementAndGet();
        lastManagementRequestAt = at;
    }

    public void noteSession(String windowsDeviceId, Instant at) {
        records.noteSession(windowsDeviceId, at);
        devices.remove(windowsDeviceId);
    }

    public long managementRequests() {
        return managementRequests.get();
    }

    public Instant lastManagementRequestAt() {
        return lastManagementRequestAt;
    }
}
