package com.story.admin.service;

import static com.story.admin.domain.AssetStatus.NORMAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.story.admin.domain.Asset;
import com.story.admin.domain.AssetCategory;
import com.story.admin.domain.CharacterIdentity;
import com.story.admin.domain.IdentityAssetRel;
import com.story.admin.exception.ConflictException;
import com.story.admin.repository.AssetCategoryRepository;
import com.story.admin.repository.AssetRepository;
import com.story.admin.repository.CharacterIdentityRepository;
import com.story.admin.repository.IdentityAssetRelRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.datasource.url=jdbc:h2:mem:story_admin_asset_hard_delete_identity_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
      "story.storage.root=../storage"
    })
class AssetHardDeleteIdentityTest {

  @Autowired AssetService assetService;
  @Autowired AssetRepository assetRepository;
  @Autowired AssetCategoryRepository categoryRepository;
  @Autowired CharacterIdentityRepository identityRepository;
  @Autowired IdentityAssetRelRepository identityAssetRelRepository;

  @Test
  void hardDeleteBlockedWhenUsedByIdentity() {
    Long assetId = persistAsset("id-ref-asset").getId();
    CharacterIdentity identity = new CharacterIdentity();
    identity.setCode("ID-0099");
    identity.setName("怪盗女孩");
    identity = identityRepository.save(identity);
    identityAssetRelRepository.save(new IdentityAssetRel(identity.getId(), assetId));

    assertThatThrownBy(() -> assetService.hardDelete(assetId))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("怪盗女孩");

    assertThat(assetRepository.findById(assetId)).isPresent();
  }

  private Asset persistAsset(String name) {
    AssetCategory category = new AssetCategory();
    category.setCode("hdi-" + name);
    category.setName(name);
    category.setSortOrder(1);
    category.setSystemPreset(false);
    category = categoryRepository.save(category);

    Asset asset = new Asset();
    asset.setDisplayName(name);
    asset.setCategoryId(category.getId());
    asset.setSortOrder(0);
    asset.setStatus(NORMAL);
    asset.setStoragePath("assets/test/" + name + ".png");
    asset.setContentType("image/png");
    return assetRepository.save(asset);
  }
}
