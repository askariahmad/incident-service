package com.devops.incident.service;

import com.devops.incident.model.LogIssue;
import com.devops.incident.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class JiraSyncService {
    
    private static final Logger log = LoggerFactory.getLogger(JiraSyncService.class);

    private final IncidentRepository incidentRepository;
    private final WebClient webClient;

    public JiraSyncService(IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;
        this.webClient = WebClient.builder().build();
    }
    
    // config-service URL
    private final String CONFIG_SERVICE_URL = "http://config-service:8082/api/v1/config";

    public Mono<SystemConfigDto> getConfig(String tenantId) {
        return webClient.get()
                .uri(CONFIG_SERVICE_URL)
                .header("X-Tenant-Id", tenantId)
                .retrieve()
                .bodyToMono(SystemConfigDto.class)
                .onErrorResume(e -> {
                    log.error("Failed to fetch config for tenant {}: {}", tenantId, e.getMessage());
                    return Mono.empty();
                });
    }

    public void createJiraTicketIfConfigured(LogIssue issue) {
        getConfig(issue.getTenantId()).subscribe(config -> {
            if (config != null && config.getJiraUrl() != null && !config.getJiraUrl().isEmpty()
                    && config.getJiraToken() != null && !config.getJiraToken().isEmpty()) {

                String projectKey = config.getJiraProjectKey() != null && !config.getJiraProjectKey().isEmpty()
                        ? config.getJiraProjectKey()
                        : "SCRUM";

                log.info("Creating Jira ticket for issue {} - project={}, email={}, url={}",
                        issue.getId(), projectKey, config.getJiraEmail(), config.getJiraUrl());

                // Build ADF (Atlassian Document Format) description - required for API v3 / next-gen projects
                Map<String, Object> textNode = new HashMap<>();
                textNode.put("type", "text");
                textNode.put("text", buildPlainDescription(issue));

                Map<String, Object> paragraph = new HashMap<>();
                paragraph.put("type", "paragraph");
                paragraph.put("content", java.util.List.of(textNode));

                Map<String, Object> description = new HashMap<>();
                description.put("type", "doc");
                description.put("version", 1);
                description.put("content", java.util.List.of(paragraph));

                Map<String, Object> fields = new HashMap<>();
                fields.put("project", java.util.Map.of("key", projectKey));
                fields.put("summary", "[DevOps-Pro] " + issue.getTitle());
                fields.put("description", description);
                fields.put("issuetype", java.util.Map.of("name", "Task")); // Task works in all project types

                Map<String, Object> body = new HashMap<>();
                body.put("fields", fields);

                String authHeader;
                if (config.getJiraEmail() != null && !config.getJiraEmail().isEmpty()) {
                    String authStr = config.getJiraEmail() + ":" + config.getJiraToken();
                    authHeader = "Basic " + Base64.getEncoder().encodeToString(authStr.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                } else {
                    authHeader = "Bearer " + config.getJiraToken();
                }

                String baseUrl = config.getJiraUrl().endsWith("/")
                        ? config.getJiraUrl().substring(0, config.getJiraUrl().length() - 1)
                        : config.getJiraUrl();
                // Use API v3 - required for next-gen (team-managed) Jira Cloud projects
                String apiUrl = baseUrl + "/rest/api/3/issue";

                log.info("POSTing to Jira: {} with project key: {}", apiUrl, projectKey);

                webClient.post()
                        .uri(apiUrl)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .header("Accept", "application/json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .subscribe(response -> {
                            if (response.containsKey("key")) {
                                String issueKey = (String) response.get("key");
                                String issueUrl = baseUrl + "/browse/" + issueKey;
                                issue.setJiraTicketKey(issueKey);
                                issue.setJiraTicketUrl(issueUrl);
                                incidentRepository.save(issue);
                                log.info("Successfully created Jira ticket {} for issue {}", issueKey, issue.getId());
                            } else {
                                log.warn("Jira response did not contain a key: {}", response);
                            }
                        }, error -> {
                            log.error("Error creating Jira ticket for issue {}: {}", issue.getId(), error.getMessage());
                            if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
                                log.error("Jira Response Body: {}", ((org.springframework.web.reactive.function.client.WebClientResponseException) error).getResponseBodyAsString());
                            }
                        });
            } else {
                log.info("Jira is not configured for tenant {}. Skipping ticket creation.", issue.getTenantId());
            }
        });
    }

    public void pushSeverityUpdate(LogIssue issue) {
        if (issue.getJiraTicketKey() == null) return;
        
        getConfig(issue.getTenantId()).subscribe(config -> {
            if (config != null && config.getJiraUrl() != null && config.getJiraToken() != null) {
                
                String authHeader;
                if (config.getJiraEmail() != null && !config.getJiraEmail().isEmpty()) {
                    String authStr = config.getJiraEmail() + ":" + config.getJiraToken();
                    authHeader = "Basic " + Base64.getEncoder().encodeToString(authStr.getBytes());
                } else if (config.getJiraToken().contains(":")) {
                    authHeader = "Basic " + Base64.getEncoder().encodeToString(config.getJiraToken().getBytes());
                } else {
                    authHeader = "Bearer " + config.getJiraToken();
                }
                
                String apiUrl = config.getJiraUrl().endsWith("/") 
                        ? config.getJiraUrl() + "rest/api/2/issue/" + issue.getJiraTicketKey()
                        : config.getJiraUrl() + "/rest/api/2/issue/" + issue.getJiraTicketKey();
                        
                // Note: Jira severity updates require specific custom fields or priorities based on Jira configuration.
                // This is a simulated update that would map your severity to Jira's priority field.
                Map<String, Object> body = new HashMap<>();
                Map<String, Object> update = new HashMap<>();
                Map<String, Object> labelsUpdate = new HashMap<>();
                labelsUpdate.put("add", "severity-" + issue.getSeverity().toLowerCase());
                update.put("labels", new Object[]{labelsUpdate});
                body.put("update", update);

                log.info("Pushing severity update to Jira for ticket {}", issue.getJiraTicketKey());

                webClient.put()
                        .uri(apiUrl)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(Void.class)
                        .subscribe(v -> log.info("Successfully updated Jira ticket {}", issue.getJiraTicketKey()),
                                err -> log.error("Error updating Jira ticket {}: {}", issue.getJiraTicketKey(), err.getMessage()));
            }
        });
    }

    public Mono<Void> updateDescription(LogIssue issue) {
        if (issue.getJiraTicketKey() == null) return Mono.empty();
        
        return getConfig(issue.getTenantId()).flatMap(config -> {
            if (config != null && config.getJiraUrl() != null && config.getJiraToken() != null) {
                
                String authHeader;
                if (config.getJiraEmail() != null && !config.getJiraEmail().isEmpty()) {
                    String authStr = config.getJiraEmail() + ":" + config.getJiraToken();
                    authHeader = "Basic " + Base64.getEncoder().encodeToString(authStr.getBytes());
                } else if (config.getJiraToken().contains(":")) {
                    authHeader = "Basic " + Base64.getEncoder().encodeToString(config.getJiraToken().getBytes());
                } else {
                    authHeader = "Bearer " + config.getJiraToken();
                }
                
                String apiUrl = config.getJiraUrl().endsWith("/") 
                        ? config.getJiraUrl() + "rest/api/2/issue/" + issue.getJiraTicketKey()
                        : config.getJiraUrl() + "/rest/api/2/issue/" + issue.getJiraTicketKey();
                        
                Map<String, Object> body = new HashMap<>();
                Map<String, Object> fields = new HashMap<>();
                
                String description = "";
                if (issue.getWhy() != null) description += "h2. WHY\n" + issue.getWhy() + "\n\n";
                if (issue.getHowToFix() != null) description += "h2. HOW TO FIX\n" + issue.getHowToFix() + "\n\n";
                
                fields.put("description", markdownToJira(description));
                body.put("fields", fields);

                log.info("Pushing description update to Jira for ticket {}", issue.getJiraTicketKey());

                return webClient.put()
                        .uri(apiUrl)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(Void.class)
                        .doOnSuccess(v -> log.info("Successfully updated Jira ticket description {}", issue.getJiraTicketKey()))
                        .doOnError(err -> log.error("Error updating Jira ticket description {}: {}", issue.getJiraTicketKey(), err.getMessage()));
            }
            return Mono.empty();
        });
    }

    public Mono<Void> forceSync(LogIssue issue) {
        if (issue.getJiraTicketKey() == null) return Mono.empty();
        
        return getConfig(issue.getTenantId()).flatMap(config -> {
            if (config != null && config.getJiraUrl() != null && config.getJiraToken() != null) {
                
                String authHeader;
                if (config.getJiraEmail() != null && !config.getJiraEmail().isEmpty()) {
                    String authStr = config.getJiraEmail() + ":" + config.getJiraToken();
                    authHeader = "Basic " + Base64.getEncoder().encodeToString(authStr.getBytes());
                } else if (config.getJiraToken().contains(":")) {
                    authHeader = "Basic " + Base64.getEncoder().encodeToString(config.getJiraToken().getBytes());
                } else {
                    authHeader = "Bearer " + config.getJiraToken();
                }
                
                String apiUrl = config.getJiraUrl().endsWith("/") 
                        ? config.getJiraUrl() + "rest/api/2/issue/" + issue.getJiraTicketKey()
                        : config.getJiraUrl() + "/rest/api/2/issue/" + issue.getJiraTicketKey();
                        
                log.info("Force syncing from Jira for ticket {}", issue.getJiraTicketKey());

                return webClient.get()
                        .uri(apiUrl)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .doOnNext(response -> {
                            if (response.containsKey("fields")) {
                                Map<String, Object> fields = (Map<String, Object>) response.get("fields");
                                if (fields.containsKey("summary")) {
                                    String summary = (String) fields.get("summary");
                                    issue.setTitle(summary.replace("[DevOps-Pro] ", ""));
                                }
                                
                                if (fields.containsKey("status") && fields.get("status") != null) {
                                    Map<String, Object> statusObj = (Map<String, Object>) fields.get("status");
                                    issue.setJiraStatus((String) statusObj.get("name"));
                                }
                                
                                if (fields.containsKey("assignee") && fields.get("assignee") != null) {
                                    Map<String, Object> assigneeObj = (Map<String, Object>) fields.get("assignee");
                                    issue.setJiraAssignee((String) assigneeObj.get("displayName"));
                                } else {
                                    issue.setJiraAssignee("Unassigned");
                                }
                                
                                if (fields.containsKey("comment") && fields.get("comment") != null) {
                                    Map<String, Object> commentBlock = (Map<String, Object>) fields.get("comment");
                                    if (commentBlock.containsKey("comments")) {
                                        java.util.List<Map<String, Object>> jiraComments = (java.util.List<Map<String, Object>>) commentBlock.get("comments");
                                        java.util.List<LogIssue.JiraComment> localComments = new java.util.ArrayList<>();
                                        for (Map<String, Object> jc : jiraComments) {
                                            LogIssue.JiraComment localC = new LogIssue.JiraComment();
                                            localC.setId((String) jc.get("id"));
                                            localC.setBody((String) jc.get("body"));
                                            
                                            if (jc.containsKey("author") && jc.get("author") != null) {
                                                Map<String, Object> authorObj = (Map<String, Object>) jc.get("author");
                                                localC.setAuthor((String) authorObj.get("displayName"));
                                            } else {
                                                localC.setAuthor("Unknown");
                                            }
                                            
                                            if (jc.containsKey("created")) {
                                                try {
                                                    // Simple parse, might need DateTimeFormatter if strictly ISO8601
                                                    localC.setCreated(java.util.Date.from(java.time.ZonedDateTime.parse((String) jc.get("created")).toInstant()));
                                                } catch (Exception e) {
                                                    log.warn("Failed to parse date: " + jc.get("created"));
                                                }
                                            }
                                            localComments.add(localC);
                                        }
                                        issue.setJiraComments(localComments);
                                    }
                                }
                                
                                incidentRepository.save(issue);
                                log.info("Successfully synced Jira ticket {}", issue.getJiraTicketKey());
                            }
                        }).then();
            }
            return Mono.empty();
        });
    }

    public Mono<Void> addComment(LogIssue issue, String commentBody) {
        if (issue.getJiraTicketKey() == null) return Mono.empty();
        
        return getConfig(issue.getTenantId()).flatMap(config -> {
            if (config != null && config.getJiraUrl() != null && config.getJiraToken() != null) {
                String authHeader = getAuthHeader(config);
                String apiUrl = getBaseUrl(config) + "/rest/api/2/issue/" + issue.getJiraTicketKey() + "/comment";
                        
                Map<String, Object> body = new HashMap<>();
                body.put("body", commentBody);

                return webClient.post()
                        .uri(apiUrl)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(Void.class)
                        .doOnSuccess(v -> log.info("Successfully added comment to Jira ticket {}", issue.getJiraTicketKey()))
                        .onErrorResume(e -> {
                            log.error("Error adding comment to Jira {}: {}", issue.getJiraTicketKey(), e.getMessage());
                            return Mono.empty();
                        });
            }
            return Mono.empty();
        });
    }

    public Mono<java.util.List<Map<String, Object>>> getTransitions(LogIssue issue) {
        if (issue.getJiraTicketKey() == null) return Mono.just(new java.util.ArrayList<>());
        
        return getConfig(issue.getTenantId()).flatMap(config -> {
            if (config != null && config.getJiraUrl() != null && config.getJiraToken() != null) {
                String authHeader = getAuthHeader(config);
                String apiUrl = getBaseUrl(config) + "/rest/api/2/issue/" + issue.getJiraTicketKey() + "/transitions";
                        
                return webClient.get()
                        .uri(apiUrl)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .map(response -> {
                            if (response.containsKey("transitions")) {
                                return (java.util.List<Map<String, Object>>) response.get("transitions");
                            }
                            return new java.util.ArrayList<Map<String, Object>>();
                        })
                        .onErrorResume(e -> {
                            log.error("Error fetching transitions from Jira {}: {}", issue.getJiraTicketKey(), e.getMessage());
                            return Mono.just(new java.util.ArrayList<>());
                        });
            }
            return Mono.just(new java.util.ArrayList<>());
        });
    }

    public Mono<Void> transitionIssue(LogIssue issue, String transitionId) {
        if (issue.getJiraTicketKey() == null) return Mono.empty();
        
        return getConfig(issue.getTenantId()).flatMap(config -> {
            if (config != null && config.getJiraUrl() != null && config.getJiraToken() != null) {
                String authHeader = getAuthHeader(config);
                String apiUrl = getBaseUrl(config) + "/rest/api/2/issue/" + issue.getJiraTicketKey() + "/transitions";
                        
                Map<String, Object> body = new HashMap<>();
                Map<String, Object> transition = new HashMap<>();
                transition.put("id", transitionId);
                body.put("transition", transition);

                return webClient.post()
                        .uri(apiUrl)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(Void.class)
                        .doOnSuccess(v -> log.info("Successfully transitioned Jira ticket {}", issue.getJiraTicketKey()))
                        .onErrorResume(e -> {
                            log.error("Error transitioning Jira {}: {}", issue.getJiraTicketKey(), e.getMessage());
                            return Mono.empty();
                        });
            }
            return Mono.empty();
        });
    }
    
    private String getAuthHeader(SystemConfigDto config) {
        if (config.getJiraEmail() != null && !config.getJiraEmail().isEmpty()) {
            String authStr = config.getJiraEmail() + ":" + config.getJiraToken();
            return "Basic " + Base64.getEncoder().encodeToString(authStr.getBytes());
        } else if (config.getJiraToken().contains(":")) {
            return "Basic " + Base64.getEncoder().encodeToString(config.getJiraToken().getBytes());
        } else {
            return "Bearer " + config.getJiraToken();
        }
    }
    
    private String getBaseUrl(SystemConfigDto config) {
        return config.getJiraUrl().endsWith("/") 
            ? config.getJiraUrl().substring(0, config.getJiraUrl().length() - 1)
            : config.getJiraUrl();
    }

    private String buildPlainDescription(LogIssue issue) {
        StringBuilder desc = new StringBuilder();
        desc.append("Issue Details\n\n");
        desc.append("Type: ").append(issue.getType()).append("\n");
        desc.append("Severity: ").append(issue.getSeverity()).append("\n");
        if (issue.getRepository() != null) {
            desc.append("Repository: ").append(issue.getRepository()).append("\n");
        }
        if (issue.getFilePath() != null) {
            desc.append("File: ").append(issue.getFilePath()).append("\n");
        }
        desc.append("\nWhat: ").append(issue.getWhat() != null ? issue.getWhat() : "N/A").append("\n");
        desc.append("Why: ").append(issue.getWhy() != null ? issue.getWhy() : "N/A").append("\n");
        desc.append("How to Fix: ").append(issue.getHowToFix() != null ? issue.getHowToFix() : "N/A").append("\n");
        desc.append("\nCreated by DevOps-Pro automated security scanner.");
        return desc.toString();
    }
    
    private String markdownToJira(String markdown) {
        if (markdown == null) return "";
        String result = markdown;
        
        // Code blocks: ```diff -> {code:diff}, ``` -> {code}
        // Only match ``` at the beginning of a line to avoid replacing backticks inside code snippets
        result = result.replaceAll("(?m)^\\s*```([a-zA-Z0-9]+)\\s*$", "{code:$1}");
        result = result.replaceAll("(?m)^\\s*```\\s*$", "{code}");
        
        // Bold: **text** -> *text*
        result = result.replaceAll("\\*\\*(.*?)\\*\\*", "*$1*");
        
        // Headings: ### Header -> h3. Header
        result = result.replaceAll("(?m)^### (.*)$", "h3. $1");
        result = result.replaceAll("(?m)^## (.*)$", "h2. $1");
        result = result.replaceAll("(?m)^# (.*)$", "h1. $1");
        
        // Links: [text](url) -> [text|url]
        result = result.replaceAll("\\[([^\\]]+)\\]\\(([^\\)]+)\\)", "[$1|$2]");
        
        return result;
    }
}
