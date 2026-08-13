package com.story.admin.dto;

import java.util.List;

public record AssetUpdateRequest(
    String displayName,
    String description,
    String chapterRefPlaceholder,
    List<String> tagNames,
    List<Long> characterIds) {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String displayName;
    private String description;
    private String chapterRefPlaceholder;
    private List<String> tagNames;
    private List<Long> characterIds;

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

    public AssetUpdateRequest build() {
      return new AssetUpdateRequest(
          displayName, description, chapterRefPlaceholder, tagNames, characterIds);
    }
  }
}
