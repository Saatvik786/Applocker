package com.applocker.service;

import com.applocker.device.EnrolledDevice;
import com.applocker.mdm.MdmCatalog;
import com.applocker.model.DeviceGroupMembership;
import com.applocker.model.DeviceMdmRecord;
import com.applocker.model.DeviceRecord;
import com.applocker.repository.DeviceGroupMembershipRepository;
import com.applocker.repository.DeviceMdmRepository;
import com.applocker.repository.DeviceRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DeviceService {

    private final DeviceRecordRepository devices;
    private final DeviceMdmRepository enrollment;
    private final DeviceGroupMembershipRepository memberships;

    public DeviceService(
            DeviceRecordRepository devices,
            DeviceMdmRepository enrollment,
            DeviceGroupMembershipRepository memberships) {
        this.devices = devices;
        this.enrollment = enrollment;
        this.memberships = memberships;
    }

    @Transactional
    public void upsert(EnrolledDevice enrolled) {
        DeviceRecord device = devices.findByWindowsDeviceId(enrolled.windowsDeviceId()).orElse(null);
        if (device == null) {
            device = new DeviceRecord();
            device.setId(UUID.randomUUID());
            device.setWindowsDeviceId(enrolled.windowsDeviceId());
            device.setEnrolledAt(enrolled.enrolledAt());
            device.setName(enrolled.name());
            device.setOsVersion(blank(enrolled.osVersion()));
            devices.save(device);
            DeviceMdmRecord mdm = new DeviceMdmRecord();
            mdm.setDeviceId(device.getId());
            copyEnrollment(mdm, enrolled);
            enrollment.save(mdm);
        } else {
            device.setName(enrolled.name());
            device.setOsVersion(blank(enrolled.osVersion()));
            devices.save(device);
            DeviceMdmRecord mdm = enrollment.findById(device.getId()).orElseGet(DeviceMdmRecord::new);
            if (mdm.getDeviceId() == null) {
                mdm.setDeviceId(device.getId());
            }
            copyEnrollment(mdm, enrolled);
            enrollment.save(mdm);
        }
        assignEnrolledGroup(device.getId());
    }

    @Transactional
    public void noteSession(String windowsDeviceId, Instant at) {
        DeviceRecord device = devices.findByWindowsDeviceId(windowsDeviceId).orElse(null);
        if (device == null) {
            device = new DeviceRecord();
            device.setId(UUID.randomUUID());
            device.setWindowsDeviceId(windowsDeviceId);
            device.setName(windowsDeviceId);
            device.setEnrolledAt(at);
            devices.save(device);
            DeviceMdmRecord mdm = new DeviceMdmRecord();
            mdm.setDeviceId(device.getId());
            mdm.setLastSessionAt(at);
            enrollment.save(mdm);
            assignEnrolledGroup(device.getId());
            return;
        }
        DeviceMdmRecord mdm = enrollment.findById(device.getId()).orElseGet(DeviceMdmRecord::new);
        if (mdm.getDeviceId() == null) {
            mdm.setDeviceId(device.getId());
        }
        mdm.setLastSessionAt(at);
        enrollment.save(mdm);
    }

    @Transactional(readOnly = true)
    public EnrolledDevice find(String windowsDeviceId) {
        return devices.findByWindowsDeviceId(windowsDeviceId).map(this::toEnrolled).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<EnrolledDevice> list() {
        return devices.findAllByOrderByEnrolledAtAsc().stream().map(this::toEnrolled).toList();
    }

    private void assignEnrolledGroup(UUID deviceId) {
        DeviceGroupMembership.Key key = new DeviceGroupMembership.Key(deviceId, MdmCatalog.ENROLLED_DEVICES);
        if (memberships.existsById(key)) {
            return;
        }
        DeviceGroupMembership membership = new DeviceGroupMembership();
        membership.setDeviceId(deviceId);
        membership.setDeviceGroupId(MdmCatalog.ENROLLED_DEVICES);
        memberships.save(membership);
    }

    private EnrolledDevice toEnrolled(DeviceRecord device) {
        DeviceMdmRecord mdm = enrollment.findById(device.getId()).orElse(null);
        return new EnrolledDevice(
                device.getWindowsDeviceId(),
                device.getName(),
                device.getOsVersion(),
                mdm == null ? null : mdm.getEnrollmentType(),
                mdm == null ? null : mdm.getTokenLabel(),
                device.getEnrolledAt(),
                mdm == null ? null : mdm.getCertificateNotAfter(),
                mdm == null ? null : mdm.getCertificateThumbprint());
    }

    private void copyEnrollment(DeviceMdmRecord mdm, EnrolledDevice enrolled) {
        mdm.setEnrollmentType(blank(enrolled.enrollmentType()));
        mdm.setTokenLabel(blank(enrolled.tokenLabel()));
        mdm.setCertificateThumbprint(blank(enrolled.certificateThumbprint()));
        mdm.setCertificateNotAfter(enrolled.certificateNotAfter());
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
