package com.story.admin.domain;

import java.io.Serializable;
import java.util.Objects;

public class PageAssetRefId implements Serializable {

  private Long pageId;
  private Long assetId;
  private String refKind;

  public PageAssetRefId() {}

  public PageAssetRefId(Long pageId, Long assetId, String refKind) {
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof PageAssetRefId that)) {
      return false;
    }
    return Objects.equals(pageId, that.pageId)
        && Objects.equals(assetId, that.assetId)
        && Objects.equals(refKind, that.refKind);
  }

  @Override
  public int hashCode() {
    return Objects.hash(pageId, assetId, refKind);
  }
}
