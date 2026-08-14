package com.story.admin.dto;

import com.story.admin.domain.AssetLinkType;
import java.util.List;

public record AssetUpdateRequest(
    String displayName,
    String description,
    String chapterRefPlaceholder,
    List<String> tagNames,
    List<Long> characterIds,
    AssetLinkType linkType,
    List<Long> seriesIds,
    List<Long> arcIds) {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String displayName;
    private String description;
    private String chapterRefPlaceholder;
    private List<String> tagNames;
    private List<Long> characterIds;
    private AssetLinkType linkType;
    private List<Long> seriesIds;
    private List<Long> arcIds;

    public Builder displayName(String displayName) {
      this.displayName = displayName;
      return this;
    }

    public Builder description(String description) {
      this.description = description;
      return this;
    }

    public Builder chapterRefPlaceholder(String chapterRefPlaceholder) {
      this.chapterRefPlaceholder = chapterRefPlaceholder;
      return this;
    }

    public Builder tagNames(List<String> tagNames) {
      this.tagNames = tagNames;
      return this;
    }

    public Builder characterIds(List<Long> characterIds) {
      this.characterIds = characterIds;
      return this;
    }

    public Builder linkType(AssetLinkType linkType) {
      this.linkType = linkType;
      return this;
    }

    public Builder seriesIds(List<Long> seriesIds) {
      this.seriesIds = seriesIds;
      return this;
    }

    public Builder arcIds(List<Long> arcIds) {
      this.arcIds = arcIds;
      return this;
    }

    public AssetUpdateRequest build() {
      return new AssetUpdateRequest(
          displayName,
          description,
          chapterRefPlaceholder,
          tagNames,
          characterIds,
          linkType,
          seriesIds,
          arcIds);
    }
  }
}
