package com.story.admin.dto;

import java.util.List;
import java.util.Map;

public record ArcReadingStreamResponse(
    Long arcId,
    String arcTitle,
    String arcSummary,
    Long coverAssetId,
    String coverContentPath,
    int pageCount,
    List<Map<String, Object>> segments) {}
