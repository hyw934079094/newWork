package com.story.admin.domain;

import java.io.Serializable;
import java.util.Objects;

public class AssetCharacterRelId implements Serializable {

  private Long assetId;
  private Long characterId;

  public AssetCharacterRelId() {}

  public AssetCharacterRelId(Long assetId, Long characterId) {
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AssetCharacterRelId that)) {
      return false;
    }
    return Objects.equals(assetId, that.assetId) && Objects.equals(characterId, that.characterId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(assetId, characterId);
  }
}
