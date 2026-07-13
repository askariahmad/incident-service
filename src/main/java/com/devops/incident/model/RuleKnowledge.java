package com.devops.incident.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.Date;

@Document(collection = "ruleKnowledge")
public class RuleKnowledge {
    
    @Id
    private String ruleKey; // The SonarQube rule key
    
    private String why;
    private String howToFix;
    private String organization;
    private Date createdAt;
    public String getRuleKey() { return ruleKey; }
    public void setRuleKey(String ruleKey) { this.ruleKey = ruleKey; }
    public String getWhy() { return why; }
    public void setWhy(String why) { this.why = why; }
    public String getHowToFix() { return howToFix; }
    public void setHowToFix(String howToFix) { this.howToFix = howToFix; }
    public String getOrganization() { return organization; }
    public void setOrganization(String organization) { this.organization = organization; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}