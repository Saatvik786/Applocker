package com.applocker.enrollment;

public record CreateEnrollmentTokenRequest(String label, Integer deviceLimit, Integer ttlHours, Integer ttlDays) {
}
