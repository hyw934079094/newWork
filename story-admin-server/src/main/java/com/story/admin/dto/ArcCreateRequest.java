package com.story.admin.dto;

import com.story.admin.domain.ArcStatus;

public record ArcCreateRequest(
    String title, ArcStatus status, String summary, Long coverAssetId) {}
