package com.story.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "asset_arc_rel")
@IdClass(AssetArcRelId.class)
public class AssetArcRel {

  @Id
  @Column(name = "asset_id", nullable = false)
  private Long assetId;

  @Id
  @Column(name = "arc_id", nullable = false)
  private Long arcId;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  public AssetArcRel() {}

  public AssetArcRel(Long assetId, Long arcId) {
    this(assetId, arcId, 0);
  }

  public AssetArcRel(Long assetId, Long arcId, int sortOrder) {
    this.assetId = assetId;
    this.arcId = arcId;
    this.sortOrder = sortOrder;
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

  public int getSortOrder() {
    return sortOrder;
  }

  public void setSortOrder(int sortOrder) {
    this.sortOrder = sortOrder;
  }
}
