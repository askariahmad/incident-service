package com.devops.incident.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;

@Document(collection = "incidents")
public class LogIssue {
    @Id
    private String id;
    
    private String tenantId;
    
    private String type; // "LOG" or "VULNERABILITY"
    
    // AI Reasoning Fields
    private String title;
    private String what;
    private String why;
    private String where; // Could be component name
    private String repository; // GitHub repo name
    private String filePath;   // File path in the repo
    
    private String rule;
    private String organization;
    private Integer startLine;
    private Integer endLine;
    private String exactCodeFix;
    
    private String howToFix;
    private String severity;
    
    private String rawData;
    
    // Deduplication Fields
    private int occurrences;
    private double matchScore;
    
    // Jira Integration
    private String jiraTicketKey;
    private String jiraTicketUrl;
    private String jiraStatus;
    private String jiraAssignee;
    private List<JiraComment> jiraComments = new ArrayList<>();
    
    public static class JiraComment {
        private String id;
        private String author;
        private String body;
        private Date created;
        
        public JiraComment() {}
        
        public JiraComment(String id, String author, String body, Date created) {
            this.id = id;
            this.author = author;
            this.body = body;
            this.created = created;
        }
        
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }
        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
        public Date getCreated() { return created; }
        public void setCreated(Date created) { this.created = created; }
    }
    
    private Date firstSeen;
    private Date lastSeen;
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getWhat() { return what; }
    public void setWhat(String what) { this.what = what; }

    public String getWhy() { return why; }
    public void setWhy(String why) { this.why = why; }

    public String getWhere() { return where; }
    public void setWhere(String where) { this.where = where; }

    public String getRepository() { return repository; }
    public void setRepository(String repository) { this.repository = repository; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getRule() { return rule; }
    public void setRule(String rule) { this.rule = rule; }

    public String getOrganization() { return organization; }
    public void setOrganization(String organization) { this.organization = organization; }

    public Integer getStartLine() { return startLine; }
    public void setStartLine(Integer startLine) { this.startLine = startLine; }

    public Integer getEndLine() { return endLine; }
    public void setEndLine(Integer endLine) { this.endLine = endLine; }

    public String getExactCodeFix() { return exactCodeFix; }
    public void setExactCodeFix(String exactCodeFix) { this.exactCodeFix = exactCodeFix; }

    public String getHowToFix() { return howToFix; }
    public void setHowToFix(String howToFix) { this.howToFix = howToFix; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getRawData() { return rawData; }
    public void setRawData(String rawData) { this.rawData = rawData; }

    public int getOccurrences() { return occurrences; }
    public void setOccurrences(int occurrences) { this.occurrences = occurrences; }

    public double getMatchScore() { return matchScore; }
    public void setMatchScore(double matchScore) { this.matchScore = matchScore; }

    public String getJiraTicketKey() { return jiraTicketKey; }
    public void setJiraTicketKey(String jiraTicketKey) { this.jiraTicketKey = jiraTicketKey; }

    public String getJiraTicketUrl() { return jiraTicketUrl; }
    public void setJiraTicketUrl(String jiraTicketUrl) { this.jiraTicketUrl = jiraTicketUrl; }

    public String getJiraStatus() { return jiraStatus; }
    public void setJiraStatus(String jiraStatus) { this.jiraStatus = jiraStatus; }

    public String getJiraAssignee() { return jiraAssignee; }
    public void setJiraAssignee(String jiraAssignee) { this.jiraAssignee = jiraAssignee; }

    public List<JiraComment> getJiraComments() { return jiraComments; }
    public void setJiraComments(List<JiraComment> jiraComments) { this.jiraComments = jiraComments; }

    public Date getFirstSeen() { return firstSeen; }
    public void setFirstSeen(Date firstSeen) { this.firstSeen = firstSeen; }

    public Date getLastSeen() { return lastSeen; }
    public void setLastSeen(Date lastSeen) { this.lastSeen = lastSeen; }
}
