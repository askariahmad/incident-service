package com.devops.incident.controller;

import com.devops.incident.model.RuleKnowledge;
import com.devops.incident.repository.RuleKnowledgeRepository;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/rules")
public class RuleKnowledgeController {

    private static final Logger log = LoggerFactory.getLogger(RuleKnowledgeController.class);
    private final RuleKnowledgeRepository repository;

    public RuleKnowledgeController(RuleKnowledgeRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/{ruleKey}")
    public ResponseEntity<RuleKnowledge> getRuleKnowledge(@PathVariable String ruleKey) {
        Optional<RuleKnowledge> ruleOpt = repository.findById(ruleKey);
        return ruleOpt.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<RuleKnowledge> saveRuleKnowledge(@RequestBody RuleKnowledge ruleKnowledge) {
        if (ruleKnowledge.getRuleKey() == null || ruleKnowledge.getRuleKey().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        Optional<RuleKnowledge> existing = repository.findById(ruleKnowledge.getRuleKey());
        if (existing.isPresent()) {
            RuleKnowledge r = existing.get();
            r.setWhy(ruleKnowledge.getWhy());
            r.setHowToFix(ruleKnowledge.getHowToFix());
            r.setOrganization(ruleKnowledge.getOrganization());
            return ResponseEntity.ok(repository.save(r));
        } else {
            ruleKnowledge.setCreatedAt(new Date());
            return ResponseEntity.ok(repository.save(ruleKnowledge));
        }
    }
}
