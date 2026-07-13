package com.devops.incident.repository;

import com.devops.incident.model.RuleKnowledge;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RuleKnowledgeRepository extends MongoRepository<RuleKnowledge, String> {
}
