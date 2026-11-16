package com.applocker.common.health;

public record HealthResponse(String status, String application, String environment) {
}
