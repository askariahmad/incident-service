package com.devops.incident.controller;

import com.devops.incident.model.LogIssue;
import com.devops.incident.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private static final Logger log = LoggerFactory.getLogger(IncidentController.class);
    private final IncidentRepository incidentRepository;
    private final com.devops.incident.service.JiraSyncService jiraSyncService;
    private final com.devops.incident.service.NotificationClient notificationClient;

    public IncidentController(IncidentRepository incidentRepository, 
                              com.devops.incident.service.JiraSyncService jiraSyncService,
                              com.devops.incident.service.NotificationClient notificationClient) {
        this.incidentRepository = incidentRepository;
        this.jiraSyncService = jiraSyncService;
        this.notificationClient = notificationClient;
    }

    @PostMapping
    public ResponseEntity<Void> receiveIncident(@RequestHeader(value = "X-Tenant-Id", required = false) String tenantId, 
                                                @RequestBody LogIssue newIssue) {
        if (tenantId == null) return ResponseEntity.badRequest().build();
        log.info("Received new incident for tenant {}: {}", tenantId, newIssue.getTitle());
        
        List<LogIssue> existing = incidentRepository.findByTenantId(tenantId);
        for (LogIssue issue : existing) {
            if (issue.getTitle().contains(newIssue.getTitle()) || newIssue.getTitle().contains(issue.getTitle())) {
                issue.setOccurrences(issue.getOccurrences() + 1);
                issue.setLastSeen(new Date());
                incidentRepository.save(issue);
                return ResponseEntity.ok().build();
            }
        }
        
        newIssue.setTenantId(tenantId);
        newIssue.setFirstSeen(new Date());
        newIssue.setLastSeen(new Date());
        newIssue.setOccurrences(1);
        
        incidentRepository.save(newIssue);
        
        notificationClient.sendNotification(tenantId, 
                "New Issue Detected", 
                "A new " + newIssue.getSeverity() + " severity issue: " + newIssue.getTitle(), 
                "CRITICAL".equals(newIssue.getSeverity()) || "HIGH".equals(newIssue.getSeverity()) ? "ERROR" : "WARNING");

        if ("HIGH".equalsIgnoreCase(newIssue.getSeverity()) || "CRITICAL".equalsIgnoreCase(newIssue.getSeverity())) {
            jiraSyncService.createJiraTicketIfConfigured(newIssue);
        }
        
        return ResponseEntity.ok().build();
    }

    @PostMapping("/batch")
    public ResponseEntity<Void> receiveIncidentsBatch(@RequestHeader(value = "X-Tenant-Id", required = false) String tenantId, 
                                                      @RequestBody List<LogIssue> issues) {
        if (tenantId == null) return ResponseEntity.badRequest().build();
        for (LogIssue issue : issues) {
            receiveIncident(tenantId, issue);
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<LogIssue>> getAllIncidents(@RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        if (tenantId == null) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(incidentRepository.findByTenantId(tenantId));
    }

    @GetMapping("/repo")
    public ResponseEntity<List<LogIssue>> getRepoIncidents(@RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
                                                           @RequestParam String repository) {
        if (tenantId == null) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(incidentRepository.findByTenantIdAndRepository(tenantId, repository));
    }

    @PostMapping("/{id}/jira")
    public ResponseEntity<Void> createJiraTicketManually(@PathVariable String id) {
        return incidentRepository.findById(id).map(issue -> {
            jiraSyncService.createJiraTicketIfConfigured(issue);
            return ResponseEntity.ok().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/severity")
    public ResponseEntity<Void> updateSeverity(@PathVariable String id, @RequestBody java.util.Map<String, String> body) {
        String newSeverity = body.get("severity");
        if (newSeverity == null) return ResponseEntity.badRequest().build();

        return incidentRepository.findById(id).map(issue -> {
            issue.setSeverity(newSeverity);
            incidentRepository.save(issue);
            jiraSyncService.pushSeverityUpdate(issue);
            return ResponseEntity.ok().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/description")
    public reactor.core.publisher.Mono<ResponseEntity<Void>> updateDescription(@PathVariable String id, @RequestBody java.util.Map<String, String> body) {
        String newWhy = body.get("why");
        String newHowToFix = body.get("howToFix");
        
        java.util.Optional<LogIssue> issueOpt = incidentRepository.findById(id);
        if (issueOpt.isEmpty()) {
            return reactor.core.publisher.Mono.just(ResponseEntity.notFound().build());
        }
        
        LogIssue issue = issueOpt.get();
        if (newWhy != null) issue.setWhy(newWhy);
        if (newHowToFix != null) issue.setHowToFix(newHowToFix);
        
        incidentRepository.save(issue);
        
        if (issue.getJiraTicketKey() != null) {
            return jiraSyncService.updateDescription(issue)
                    .thenReturn(ResponseEntity.ok().<Void>build())
                    .onErrorReturn(ResponseEntity.internalServerError().build());
        }
        return reactor.core.publisher.Mono.just(ResponseEntity.ok().<Void>build());
    }

    @PostMapping("/{id}/jira/sync")
    public reactor.core.publisher.Mono<ResponseEntity<Void>> syncJiraTicket(@PathVariable String id) {
        java.util.Optional<LogIssue> issueOpt = incidentRepository.findById(id);
        if (issueOpt.isEmpty()) {
            return reactor.core.publisher.Mono.just(ResponseEntity.notFound().build());
        }
        return jiraSyncService.forceSync(issueOpt.get())
                .thenReturn(ResponseEntity.ok().<Void>build())
                .onErrorReturn(ResponseEntity.internalServerError().build());
    }

    @PostMapping("/{id}/jira/comments")
    public reactor.core.publisher.Mono<ResponseEntity<Void>> addJiraComment(@PathVariable String id, @RequestBody java.util.Map<String, String> body) {
        String comment = body.get("body");
        if (comment == null) return reactor.core.publisher.Mono.just(ResponseEntity.badRequest().build());
        
        java.util.Optional<LogIssue> issueOpt = incidentRepository.findById(id);
        if (issueOpt.isEmpty()) {
            return reactor.core.publisher.Mono.just(ResponseEntity.notFound().build());
        }
        // Add to Jira then sync back
        return jiraSyncService.addComment(issueOpt.get(), comment)
                .then(jiraSyncService.forceSync(issueOpt.get()))
                .thenReturn(ResponseEntity.ok().<Void>build());
    }

    @GetMapping("/{id}/jira/transitions")
    public reactor.core.publisher.Mono<ResponseEntity<java.util.List<java.util.Map<String, Object>>>> getJiraTransitions(@PathVariable String id) {
        java.util.Optional<LogIssue> issueOpt = incidentRepository.findById(id);
        if (issueOpt.isEmpty()) {
            return reactor.core.publisher.Mono.just(ResponseEntity.notFound().build());
        }
        return jiraSyncService.getTransitions(issueOpt.get())
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.ok(new java.util.ArrayList<>()));
    }

    @PostMapping("/{id}/jira/transitions")
    public reactor.core.publisher.Mono<ResponseEntity<Void>> transitionJiraTicket(@PathVariable String id, @RequestBody java.util.Map<String, String> body) {
        String transitionId = body.get("transitionId");
        if (transitionId == null) return reactor.core.publisher.Mono.just(ResponseEntity.badRequest().build());
        
        java.util.Optional<LogIssue> issueOpt = incidentRepository.findById(id);
        if (issueOpt.isEmpty()) {
            return reactor.core.publisher.Mono.just(ResponseEntity.notFound().build());
        }
        // Transition then sync back to get new status
        return jiraSyncService.transitionIssue(issueOpt.get(), transitionId)
                .then(jiraSyncService.forceSync(issueOpt.get()))
                .thenReturn(ResponseEntity.ok().<Void>build());
    }
}
