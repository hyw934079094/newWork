package com.story.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "asset_series_rel")
@IdClass(AssetSeriesRelId.class)
public class AssetSeriesRel {

  @Id
  @Column(name = "asset_id", nullable = false)
  private Long assetId;

  @Id
  @Column(name = "series_id", nullable = false)
  private Long seriesId;

  public AssetSeriesRel() {}

  public AssetSeriesRel(Long assetId, Long seriesId) {
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
}
