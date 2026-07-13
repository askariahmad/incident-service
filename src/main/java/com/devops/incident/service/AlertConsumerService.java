package com.devops.incident.service;

import com.devops.incident.model.LogIssue;
import com.devops.incident.repository.IncidentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * Kafka Consumer Service for incident-service.
 *
 * Listens to the `enriched-security-alerts` topic published by repo-scanner-service.
 * Persists each alert to MongoDB using the same deduplication logic as the REST endpoint.
 *
 * Kafka guarantees at-least-once delivery. If this service crashes mid-batch, Kafka
 * will re-deliver unacknowledged messages on restart — no data loss.
 *
 * The consumer group-id is `incident-group`, meaning all instances of incident-service
 * share the work of consuming from the topic (each message is processed by exactly one instance).
 */
@Service
public class AlertConsumerService {

    private static final Logger log = LoggerFactory.getLogger(AlertConsumerService.class);

    private final IncidentRepository incidentRepository;
    private final JiraSyncService jiraSyncService;
    private final NotificationClient notificationClient;

    public AlertConsumerService(IncidentRepository incidentRepository,
                                JiraSyncService jiraSyncService,
                                NotificationClient notificationClient) {
        this.incidentRepository = incidentRepository;
        this.jiraSyncService = jiraSyncService;
        this.notificationClient = notificationClient;
    }

    @KafkaListener(
            topics = "enriched-security-alerts",
            groupId = "incident-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeEnrichedAlert(@Payload LogIssue alert,
                                     @Header(KafkaHeaders.RECEIVED_KEY) String tenantId) {
        log.info("[KAFKA] Consumed enriched alert '{}' for tenant '{}'", alert.getTitle(), tenantId);

        try {
            // Deduplication: check if this issue already exists for this tenant
            List<LogIssue> existing = incidentRepository.findByTenantId(tenantId);
            for (LogIssue issue : existing) {
                if (issue.getTitle() != null && alert.getTitle() != null &&
                    (issue.getTitle().contains(alert.getTitle()) || alert.getTitle().contains(issue.getTitle()))) {
                    issue.setOccurrences(issue.getOccurrences() + 1);
                    issue.setLastSeen(new Date());
                    incidentRepository.save(issue);
                    log.info("[KAFKA] Duplicate detected — incremented occurrence for '{}'", alert.getTitle());
                    return;
                }
            }

            // New issue — persist it
            alert.setTenantId(tenantId);
            if (alert.getFirstSeen() == null) alert.setFirstSeen(new Date());
            alert.setLastSeen(new Date());
            if (alert.getOccurrences() == 0) alert.setOccurrences(1);

            incidentRepository.save(alert);
            log.info("[KAFKA] Saved new issue '{}' to MongoDB for tenant '{}'", alert.getTitle(), tenantId);

            // Send notification for critical/high issues
            notificationClient.sendNotification(tenantId,
                    "New Issue Detected",
                    "A new " + alert.getSeverity() + " severity issue: " + alert.getTitle(),
                    "CRITICAL".equals(alert.getSeverity()) || "HIGH".equals(alert.getSeverity()) ? "ERROR" : "WARNING");

            // Auto-create Jira ticket for high severity
            if ("HIGH".equalsIgnoreCase(alert.getSeverity()) || "CRITICAL".equalsIgnoreCase(alert.getSeverity())) {
                jiraSyncService.createJiraTicketIfConfigured(alert);
            }

        } catch (Exception e) {
            log.error("[KAFKA] Failed to process alert '{}': {}", alert.getTitle(), e.getMessage(), e);
            // Re-throw so Kafka retries the message (does not commit the offset)
            throw new RuntimeException("Failed to process alert: " + alert.getTitle(), e);
        }
    }
}
