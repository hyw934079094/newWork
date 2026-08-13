package com.story.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "asset_tag_rel")
@IdClass(AssetTagRelId.class)
public class AssetTagRel {

  @Id
  @Column(name = "asset_id", nullable = false)
  private Long assetId;

  @Id
  @Column(name = "tag_id", nullable = false)
  private Long tagId;

  public AssetTagRel() {}

  public AssetTagRel(Long assetId, Long tagId) {
    this.assetId = assetId;
    this.tagId = tagId;
  }

  public Long getAssetId() {
    return assetId;
  }

  public void setAssetId(Long assetId) {
    this.assetId = assetId;
  }

  public Long getTagId() {
    return tagId;
  }

  public void setTagId(Long tagId) {
    this.tagId = tagId;
  }
}
