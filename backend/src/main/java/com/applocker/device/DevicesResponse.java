package com.applocker.device;

import java.time.Instant;
import java.util.List;

public record DevicesResponse(List<EnrolledDevice> devices, long managementRequests, Instant lastManagementRequestAt) {
}
