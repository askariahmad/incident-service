package com.devops.incident.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class NotificationClient {
    private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);

    private final WebClient webClient = WebClient.builder().build();
    private final String NOTIFICATION_SERVICE_URL = "http://localhost:8086/api/v1/notifications";

    public void sendNotification(String tenantId, String title, String message, String type) {
        if (tenantId == null) return;
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", title);
        payload.put("message", message);
        payload.put("type", type);
        
        webClient.post()
                .uri(NOTIFICATION_SERVICE_URL)
                .header("X-Tenant-Id", tenantId)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Void.class)
                .subscribe(v -> log.info("Notification sent to {}", tenantId),
                           e -> log.error("Failed to send notification: {}", e.getMessage()));
    }
}
