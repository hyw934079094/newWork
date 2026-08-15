package com.story.admin.domain;

import java.io.Serializable;
import java.util.Objects;

public class AssetUnlinkedOrderId implements Serializable {

  private Long categoryId;
  private Long assetId;

  public AssetUnlinkedOrderId() {}

  public AssetUnlinkedOrderId(Long categoryId, Long assetId) {
    this.categoryId = categoryId;
    this.assetId = assetId;
  }

  public Long getCategoryId() {
    return categoryId;
  }

  public void setCategoryId(Long categoryId) {
    this.categoryId = categoryId;
  }

  public Long getAssetId() {
    return assetId;
  }

  public void setAssetId(Long assetId) {
    this.assetId = assetId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AssetUnlinkedOrderId that)) {
      return false;
    }
    return Objects.equals(categoryId, that.categoryId) && Objects.equals(assetId, that.assetId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(categoryId, assetId);
  }
}
