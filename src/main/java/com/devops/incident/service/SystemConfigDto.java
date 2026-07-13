package com.devops.incident.service;

import lombok.Data;

public class SystemConfigDto {
    private String jiraUrl;
    private String jiraToken;
    private String jiraProjectKey;

    public String getJiraUrl() { return jiraUrl; }
    public void setJiraUrl(String jiraUrl) { this.jiraUrl = jiraUrl; }
    public String getJiraToken() { return jiraToken; }
    public void setJiraToken(String jiraToken) { this.jiraToken = jiraToken; }
    
    private String jiraEmail;
    public String getJiraEmail() { return jiraEmail; }
    public void setJiraEmail(String jiraEmail) { this.jiraEmail = jiraEmail; }
    
    public String getJiraProjectKey() { return jiraProjectKey; }
    public void setJiraProjectKey(String jiraProjectKey) { this.jiraProjectKey = jiraProjectKey; }
}
