package com.story.admin.dto;

import java.util.List;

public record AssetReorderRequest(Long categoryId, List<Long> orderedIds) {}
