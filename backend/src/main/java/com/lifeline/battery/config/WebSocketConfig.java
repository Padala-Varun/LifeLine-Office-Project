package com.lifeline.battery.config;

import com.lifeline.battery.dashboard.DashboardSocket;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final DashboardSocket dashboardSocket;

    public WebSocketConfig(DashboardSocket dashboardSocket) {
        this.dashboardSocket = dashboardSocket;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Same-origin only (Spring's default) and login required (SecurityConfig).
        registry.addHandler(dashboardSocket, "/ws/dashboard");
    }
}
