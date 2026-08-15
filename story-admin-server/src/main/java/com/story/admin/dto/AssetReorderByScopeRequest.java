package com.story.admin.dto;

import java.util.List;

public record AssetReorderByScopeRequest(
    Long categoryId, String scope, Long scopeId, List<Long> orderedIds) {}
