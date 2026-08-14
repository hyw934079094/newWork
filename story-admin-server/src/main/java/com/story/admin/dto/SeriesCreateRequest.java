package com.story.admin.dto;

import com.story.admin.domain.SeriesStatus;

public record SeriesCreateRequest(
    String name, SeriesStatus status, String summary, String tags, Long coverAssetId) {}
