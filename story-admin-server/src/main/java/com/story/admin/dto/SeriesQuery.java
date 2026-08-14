package com.story.admin.dto;

import com.story.admin.domain.SeriesStatus;

public record SeriesQuery(String q, SeriesStatus status) {}
