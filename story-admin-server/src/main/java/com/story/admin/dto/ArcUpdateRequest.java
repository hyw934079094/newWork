package com.story.admin.dto;

import com.story.admin.domain.ArcStatus;

public record ArcUpdateRequest(
    String title, ArcStatus status, String summary, Long coverAssetId) {}
