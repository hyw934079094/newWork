package com.story.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "page_asset_ref")
@IdClass(PageAssetRefId.class)
public class PageAssetRef {

  @Id
  @Column(name = "page_id", nullable = false)
  private Long pageId;

  @Id
  @Column(name = "asset_id", nullable = false)
  private Long assetId;

  @Id
  @Column(name = "ref_kind", nullable = false, length = 32)
  private String refKind;

  public PageAssetRef() {}

  public PageAssetRef(Long pageId, Long assetId, String refKind) {
    this.pageId = pageId;
    this.assetId = assetId;
    this.refKind = refKind;
  }

  public Long getPageId() {
    return pageId;
  }

  public void setPageId(Long pageId) {
    this.pageId = pageId;
  }

  public Long getAssetId() {
    return assetId;
  }

  public void setAssetId(Long assetId) {
    this.assetId = assetId;
  }

  public String getRefKind() {
    return refKind;
  }

  public void setRefKind(String refKind) {
    this.refKind = refKind;
  }
}
