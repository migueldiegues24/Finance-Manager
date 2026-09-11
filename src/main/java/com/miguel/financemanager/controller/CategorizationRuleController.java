package com.miguel.financemanager.controller;

import com.miguel.financemanager.dto.RuleRequest;
import com.miguel.financemanager.dto.RuleResponse;
import com.miguel.financemanager.service.CategorizationRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
public class CategorizationRuleController {

    private final CategorizationRuleService ruleService;

    @GetMapping
    public ResponseEntity<List<RuleResponse>> list() {
        return ResponseEntity.ok(ruleService.listRules());
    }

    @PostMapping
    public ResponseEntity<RuleResponse> create(@Valid @RequestBody RuleRequest request) {
        return ResponseEntity.ok(ruleService.createRule(request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        ruleService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }
}
