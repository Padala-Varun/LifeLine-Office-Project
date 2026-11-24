package com.lifeline.battery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("lifeline")
public record LifelineProperties(
        Alert alert,
        Duration offlineAfter,
        Duration offlineCheckInterval,
        Dashboard dashboard,
        String joinCode,
        boolean publicServer,
        Integer loopbackHttpPort) {

    public LifelineProperties {
        if (publicServer) {
            requireSet("LIFELINE_DASHBOARD_PASSWORD", dashboard.password());
            requireSet("LIFELINE_JOIN_CODE", joinCode);
        }
    }

    /** Spring leaves "${NAME}" in place when the variable is missing, so that counts as not set. */
    private static void requireSet(String variable, String value) {
        if (value == null || value.isBlank() || value.startsWith("${")) {
            throw new IllegalArgumentException(variable + " must be set on a public server");
        }
    }

    public record Alert(int lowBelow, int recoverAbove) {
        public Alert {
            if (recoverAbove < lowBelow) {
                throw new IllegalArgumentException("lifeline.alert.recover-above must not be lower than low-below");
            }
        }
    }

    public record Dashboard(String username, String password) {
    }
}
