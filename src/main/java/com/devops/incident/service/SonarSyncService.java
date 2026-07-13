package com.devops.incident.service;

import com.devops.incident.model.LogIssue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class SonarSyncService {
    private static final Logger log = LoggerFactory.getLogger(SonarSyncService.class);
    
    private final WebClient webClient;
    private final JiraSyncService jiraSyncService; // to get SystemConfigDto

    public SonarSyncService(JiraSyncService jiraSyncService) {
        this.webClient = WebClient.builder().build();
        this.jiraSyncService = jiraSyncService;
    }

    public Mono<Void> updateSonarStatus(LogIssue issue, String newStatusName) {
        if (issue.getSonarIssueKey() == null || issue.getSonarIssueKey().isEmpty()) {
            return Mono.empty();
        }

        return jiraSyncService.getConfig(issue.getTenantId()).flatMap(config -> {
            if (config != null && config.getSonarUrl() != null && config.getSonarToken() != null) {
                String transition = mapJiraStatusToSonarTransition(newStatusName);
                if (transition == null) {
                    log.info("No SonarQube transition mapped for Jira status: {}", newStatusName);
                    return Mono.empty();
                }

                String url = config.getSonarUrl().endsWith("/")
                        ? config.getSonarUrl() + "api/issues/do_transition"
                        : config.getSonarUrl() + "/api/issues/do_transition";
                
                String authStr = config.getSonarToken() + ":";
                String encoded = Base64.getEncoder().encodeToString(authStr.getBytes());

                log.info("Transitioning SonarQube issue {} to {}", issue.getSonarIssueKey(), transition);

                return webClient.post()
                        .uri(uriBuilder -> uriBuilder
                                .path(url.replace(config.getSonarUrl(), ""))
                                .queryParam("issue", issue.getSonarIssueKey())
                                .queryParam("transition", transition)
                                .build())
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + encoded)
                        .retrieve()
                        .bodyToMono(Void.class)
                        .doOnSuccess(v -> log.info("Successfully transitioned SonarQube issue {}", issue.getSonarIssueKey()))
                        .onErrorResume(e -> {
                            log.error("Failed to transition SonarQube issue {}: {}", issue.getSonarIssueKey(), e.getMessage());
                            return Mono.empty();
                        });
            }
            return Mono.empty();
        });
    }

    private String mapJiraStatusToSonarTransition(String jiraStatusName) {
        if (jiraStatusName == null) return null;
        String upper = jiraStatusName.toUpperCase();
        
        if (upper.contains("DONE") || upper.contains("RESOLVED") || upper.contains("CLOSED")) {
            return "resolve"; // Resolves the issue in SonarQube
        } else if (upper.contains("OPEN") || upper.contains("TODO") || upper.contains("IN PROGRESS")) {
            return "reopen"; // Reopens the issue in SonarQube
        }
        
        return null;
    }
}
