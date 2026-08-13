package com.story.admin.domain;

import java.io.Serializable;
import java.util.Objects;

public class AssetTagRelId implements Serializable {

  private Long assetId;
  private Long tagId;

  public AssetTagRelId() {}

  public AssetTagRelId(Long assetId, Long tagId) {
    this.assetId = assetId;
    this.tagId = tagId;
  }

  public Long getAssetId() {
    return assetId;
  }

  public void setAssetId(Long assetId) {
    this.assetId = assetId;
  }

  public Long getTagId() {
    return tagId;
  }

  public void setTagId(Long tagId) {
    this.tagId = tagId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AssetTagRelId that)) {
      return false;
    }
    return Objects.equals(assetId, that.assetId) && Objects.equals(tagId, that.tagId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(assetId, tagId);
  }
}
