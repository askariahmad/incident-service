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

    public IncidentController(IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;
    }

    @PostMapping
    public ResponseEntity<Void> receiveIncident(@RequestHeader(value = "X-Tenant-Id", required = false) String tenantId, 
                                                @RequestBody LogIssue newIssue) {
        if (tenantId == null) {
            return ResponseEntity.badRequest().build();
        }
        
        log.info("Received new incident analysis for tenant {}: {}", tenantId, newIssue.getTitle());
        
        // Simple Deduplication Mock Logic scoped to Tenant
        List<LogIssue> existing = incidentRepository.findByTenantId(tenantId);
        boolean duplicated = false;
        for (LogIssue issue : existing) {
            // Simple match score based on title string contains
            if (issue.getTitle().contains(newIssue.getTitle()) || newIssue.getTitle().contains(issue.getTitle())) {
                log.info("Duplicate found for tenant {}. Updating occurrences.", tenantId);
                issue.setOccurrences(issue.getOccurrences() + 1);
                issue.setLastSeen(new Date());
                incidentRepository.save(issue);
                duplicated = true;
                break;
            }
        }
        
        if (!duplicated) {
            newIssue.setTenantId(tenantId);
            newIssue.setFirstSeen(new Date());
            newIssue.setLastSeen(new Date());
            newIssue.setOccurrences(1);
            if ("HIGH".equals(newIssue.getSeverity())) {
                log.info("HIGH severity issue detected for tenant {}. Triggering Jira creation...", tenantId);
                newIssue.setJiraTicketKey("PROJ-999");
                newIssue.setJiraTicketUrl("https://jira.mock.com/browse/PROJ-999");
            }
            incidentRepository.save(newIssue);
        }
        
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<LogIssue>> getAllIncidents(@RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        if (tenantId == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(incidentRepository.findByTenantId(tenantId));
    }
}
