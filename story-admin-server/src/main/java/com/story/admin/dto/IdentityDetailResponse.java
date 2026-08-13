package com.story.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

public record IdentityDetailResponse(
    Long id,
    String code,
    String name,
    String storyName,
    String publicIntro,
    String internalNote,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    int memberCount,
    List<MemberView> members,
    List<AssetView> assets) {

  public record MemberView(
      Long characterId, String code, String name, String formLabel, int assetCount) {}

  public record AssetView(
      Long assetId, String displayName, String contentUrl, String contentType) {}
}
