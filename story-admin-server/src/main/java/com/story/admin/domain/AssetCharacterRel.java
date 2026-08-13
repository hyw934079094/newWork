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

  public AssetCharacterRel() {}

  public AssetCharacterRel(Long assetId, Long characterId) {
    this.assetId = assetId;
    this.characterId = characterId;
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
}
