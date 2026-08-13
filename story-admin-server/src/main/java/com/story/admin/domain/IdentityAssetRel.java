package com.story.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "identity_asset_rel")
@IdClass(IdentityAssetRelId.class)
public class IdentityAssetRel {

  @Id
  @Column(name = "identity_id", nullable = false)
  private Long identityId;

  @Id
  @Column(name = "asset_id", nullable = false)
  private Long assetId;

  public IdentityAssetRel() {}

  public IdentityAssetRel(Long identityId, Long assetId) {
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
}