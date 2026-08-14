package com.story.admin.domain;

import java.io.Serializable;
import java.util.Objects;

public class AssetArcRelId implements Serializable {
  private Long assetId;
  private Long arcId;

  public AssetArcRelId() {}

  public AssetArcRelId(Long assetId, Long arcId) {
    this.assetId = assetId;
    this.arcId = arcId;
  }

  public Long getAssetId() {
    return assetId;
  }

  public void setAssetId(Long assetId) {
    this.assetId = assetId;
  }

  public Long getArcId() {
    return arcId;
  }

  public void setArcId(Long arcId) {
    this.arcId = arcId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AssetArcRelId that)) return false;
    return Objects.equals(assetId, that.assetId) && Objects.equals(arcId, that.arcId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(assetId, arcId);
  }
}
