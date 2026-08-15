package com.story.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "asset_unlinked_order")
@IdClass(AssetUnlinkedOrderId.class)
public class AssetUnlinkedOrder {

  @Id
  @Column(name = "category_id", nullable = false)
  private Long categoryId;

  @Id
  @Column(name = "asset_id", nullable = false)
  private Long assetId;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  public AssetUnlinkedOrder() {}

  public AssetUnlinkedOrder(Long categoryId, Long assetId, int sortOrder) {
    this.categoryId = categoryId;
    this.assetId = assetId;
    this.sortOrder = sortOrder;
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

  public int getSortOrder() {
    return sortOrder;
  }

  public void setSortOrder(int sortOrder) {
    this.sortOrder = sortOrder;
  }
}
