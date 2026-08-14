package com.story.admin.dto;

import com.story.admin.domain.SeriesStatus;

public record SeriesUpdateRequest(
    String name, SeriesStatus status, String summary, String tags, Long coverAssetId) {}
