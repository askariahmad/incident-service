package com.devops.incident.repository;

import com.devops.incident.model.LogIssue;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IncidentRepository extends MongoRepository<LogIssue, String> {
    List<LogIssue> findByTenantId(String tenantId);
}
