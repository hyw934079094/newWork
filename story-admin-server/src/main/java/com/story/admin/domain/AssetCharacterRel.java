package com.story.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "asset_character_rel")
@IdClass(AssetCharacterRelId.class)
public class AssetCharacterRel {

  @Id
  @Column(name = "asset_id", nullable = false)
  private Long assetId;

  @Id
  @Column(name = "character_id", nullable = false)
  private Long characterId;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  public AssetCharacterRel() {}

  public AssetCharacterRel(Long assetId, Long characterId) {
    this(assetId, characterId, 0);
  }

  public AssetCharacterRel(Long assetId, Long characterId, int sortOrder) {
    this.assetId = assetId;
    this.characterId = characterId;
    this.sortOrder = sortOrder;
  }

  public Long getAssetId() {
    return assetId;
  }

  public void setAssetId(Long assetId) {
    this.assetId = assetId;
  }

  public Long getCharacterId() {
    return characterId;
  }

  public void setCharacterId(Long characterId) {
    this.characterId = characterId;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public void setSortOrder(int sortOrder) {
    this.sortOrder = sortOrder;
  }
}
