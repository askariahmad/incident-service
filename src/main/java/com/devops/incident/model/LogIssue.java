package com.devops.incident.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document(collection = "incidents")
public class LogIssue {
    @Id
    private String id;
    
    private String tenantId;
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    private String type; // "LOG" or "VULNERABILITY"
    
    // AI Reasoning Fields
    private String title;
    private String what;
    private String why;
    private String where;
    private String howToFix;
    private String severity;
    
    private String rawData;
    
    // Deduplication Fields
    private int occurrences;
    private double matchScore;
    
    // Jira Integration
    private String jiraTicketKey;
    private String jiraTicketUrl;
    
    private Date firstSeen;
    private Date lastSeen;
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public int getOccurrences() { return occurrences; }
    public void setOccurrences(int occurrences) { this.occurrences = occurrences; }
    public void setFirstSeen(Date firstSeen) { this.firstSeen = firstSeen; }
    public void setLastSeen(Date lastSeen) { this.lastSeen = lastSeen; }
    public void setJiraTicketKey(String jiraTicketKey) { this.jiraTicketKey = jiraTicketKey; }
    public void setJiraTicketUrl(String jiraTicketUrl) { this.jiraTicketUrl = jiraTicketUrl; }
}
