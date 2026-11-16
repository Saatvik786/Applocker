package com.applocker.device;

import java.time.Instant;

public record EnrolledDevice(
        String windowsDeviceId,
        String name,
        String osVersion,
        String enrollmentType,
        String tokenLabel,
        Instant enrolledAt,
        Instant certificateNotAfter,
        String certificateThumbprint) {
}
