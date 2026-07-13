package com.devops.incident.config;

import com.devops.incident.model.LogIssueDto;
import com.devops.incident.repository.LogIssueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Date;
import java.util.UUID;

@Configuration
@Profile("dev")
public class IncidentDataSeeder {

    private static final Logger logger = LoggerFactory.getLogger(IncidentDataSeeder.class);

    @Bean
    public CommandLineRunner seedIncidentDatabase(LogIssueRepository repository) {
        return args -> {
            logger.info("Initializing mock incidents for DEV environment...");

            if (repository.findByTenantId("devops-com-tenant").isEmpty()) {
                LogIssueDto issue1 = new LogIssueDto();
                issue1.setId(UUID.randomUUID().toString());
                issue1.setTenantId("devops-com-tenant");
                issue1.setTitle("SQL Injection Vulnerability in User Controller");
                issue1.setDescription("A critical SQL injection flaw was found in the login endpoint.");
                issue1.setSeverity("CRITICAL");
                issue1.setSource("SonarQube");
                issue1.setFilePath("src/main/java/com/example/UserController.java");
                issue1.setRule("java:S3649");
                issue1.setJiraTicketId("MOCK-101");
                issue1.setStatus("OPEN");
                issue1.setCreatedAt(new Date());

                LogIssueDto issue2 = new LogIssueDto();
                issue2.setId(UUID.randomUUID().toString());
                issue2.setTenantId("devops-com-tenant");
                issue2.setTitle("Hardcoded Credentials in application.properties");
                issue2.setDescription("Database passwords are hardcoded in the configuration file.");
                issue2.setSeverity("HIGH");
                issue2.setSource("SonarQube");
                issue2.setFilePath("src/main/resources/application.properties");
                issue2.setRule("java:S2068");
                issue2.setJiraTicketId("MOCK-102");
                issue2.setStatus("IN_PROGRESS");
                issue2.setCreatedAt(new Date(System.currentTimeMillis() - 86400000)); // 1 day ago

                LogIssueDto issue3 = new LogIssueDto();
                issue3.setId(UUID.randomUUID().toString());
                issue3.setTenantId("devops-com-tenant");
                issue3.setTitle("Mock Splunk: Unusual Data Egress");
                issue3.setDescription("5GB of data was transferred to an unknown external IP.");
                issue3.setSeverity("HIGH");
                issue3.setSource("Splunk");
                issue3.setStatus("OPEN");
                issue3.setCreatedAt(new Date());

                repository.save(issue1);
                repository.save(issue2);
                repository.save(issue3);

                logger.info("Seeded 3 mock incidents for devops-com-tenant.");
            } else {
                logger.info("Mock incidents already exist for devops-com-tenant.");
            }
            
            // We intentionally DO NOT seed data for real-tenant so the user can test their real GitHub/Jira integrations on a clean slate!
        };
    }
}
