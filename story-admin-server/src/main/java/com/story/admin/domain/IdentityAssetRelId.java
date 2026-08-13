package com.story.admin.domain;

import java.io.Serializable;
import java.util.Objects;

public class IdentityAssetRelId implements Serializable {

  private Long identityId;
  private Long assetId;

  public IdentityAssetRelId() {}

  public IdentityAssetRelId(Long identityId, Long assetId) {
    this.identityId = identityId;
    this.assetId = assetId;
  }

  public Long getIdentityId() {
    return identityId;
  }

  public void setIdentityId(Long identityId) {
    this.identityId = identityId;
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
    if (!(o instanceof IdentityAssetRelId that)) {
      return false;
    }
    return Objects.equals(identityId, that.identityId) && Objects.equals(assetId, that.assetId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(identityId, assetId);
  }
}