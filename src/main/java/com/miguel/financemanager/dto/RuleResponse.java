package com.miguel.financemanager.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RuleResponse {
    private Long id;
    private String keyword;
    private Long categoryId;
    private String categoryName;
    private int priority;
}
