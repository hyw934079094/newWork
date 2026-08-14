package com.story.admin.dto;

import java.util.List;

public record PageReorderRequest(List<Long> orderedIds) {}
