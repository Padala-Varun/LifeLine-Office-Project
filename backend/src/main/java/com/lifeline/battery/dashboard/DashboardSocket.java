package com.lifeline.battery.dashboard;

import com.lifeline.battery.dashboard.DashboardViews.AlertView;
import com.lifeline.battery.dashboard.DashboardViews.DeviceView;
import com.lifeline.battery.dashboard.DashboardViews.Update;
import com.lifeline.battery.device.DeviceService.DeviceChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Pushes every committed change to all open dashboards. */
@Component
public class DashboardSocket extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(DashboardSocket.class);
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int BUFFER_LIMIT_BYTES = 256 * 1024;

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final JsonMapper json;

    public DashboardSocket(JsonMapper json) {
        this.json = json;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(),
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_LIMIT_BYTES));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeviceChanged(DeviceChanged event) {
        if (sessions.isEmpty()) {
            return;
        }
        Update update = new Update(
                DeviceView.of(event.device()),
                event.alert() == null ? null : AlertView.of(event.alert()));
        TextMessage message = new TextMessage(json.writeValueAsString(update));
        sessions.values().forEach(session -> send(session, message));
    }

    private void send(WebSocketSession session, TextMessage message) {
        try {
            session.sendMessage(message);
        } catch (Exception e) {
            // One broken dashboard must not stop the others, or fail the device's request.
            log.debug("Dropping dashboard connection {}", session.getId(), e);
            sessions.remove(session.getId());
            try {
                session.close(CloseStatus.SESSION_NOT_RELIABLE);
            } catch (Exception ignored) {
                // Already gone.
            }
        }
    }
}
