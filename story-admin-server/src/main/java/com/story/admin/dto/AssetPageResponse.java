package com.story.admin.dto;

import com.story.admin.domain.Asset;
import java.util.List;

public record AssetPageResponse(List<Asset> items, int page, int size, long total) {}
