package com.story.admin.domain;

import java.io.Serializable;
import java.util.Objects;

public class AssetSeriesRelId implements Serializable {
  private Long assetId;
  private Long seriesId;

  public AssetSeriesRelId() {}

  public AssetSeriesRelId(Long assetId, Long seriesId) {
    this.assetId = assetId;
    this.seriesId = seriesId;
  }

  public Long getAssetId() {
    return assetId;
  }

  public void setAssetId(Long assetId) {
    this.assetId = assetId;
  }

  public Long getSeriesId() {
    return seriesId;
  }

  public void setSeriesId(Long seriesId) {
    this.seriesId = seriesId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AssetSeriesRelId that)) return false;
    return Objects.equals(assetId, that.assetId) && Objects.equals(seriesId, that.seriesId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(assetId, seriesId);
  }
}
