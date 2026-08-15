package com.story.admin.dto;

import com.story.admin.domain.AssetLinkType;
import java.util.List;

public record AssetBatchLinkRequest(
    List<Long> assetIds,
    AssetLinkType linkType,
    List<Long> seriesIds,
    List<Long> arcIds,
    List<Long> characterIds) {}
