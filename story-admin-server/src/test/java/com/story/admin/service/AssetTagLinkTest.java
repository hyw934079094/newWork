package com.story.admin.service;

import static com.story.admin.domain.AssetStatus.NORMAL;
import static org.assertj.core.api.Assertions.assertThat;

import com.story.admin.domain.Asset;
import com.story.admin.domain.AssetCategory;
import com.story.admin.domain.CharacterProfile;
import com.story.admin.dto.AssetUpdateRequest;
import com.story.admin.dto.CharacterCreateRequest;
import com.story.admin.repository.AssetCategoryRepository;
import com.story.admin.repository.AssetRepository;
import java.util.List;
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
      "spring.datasource.url=jdbc:h2:mem:story_admin_asset_tag_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
      "story.storage.root=../storage"
    })
class AssetTagLinkTest {

  @Autowired AssetService assetService;
  @Autowired CharacterService characterService;
  @Autowired AssetRepository assetRepository;
  @Autowired AssetCategoryRepository categoryRepository;

  @Test
  void updateTagsReplacesRelations() {
    Long id = persistAsset("tagged").getId();
    assetService.update(
        id, AssetUpdateRequest.builder().tagNames(List.of("旧标签")).build());

    assetService.update(id, AssetUpdateRequest.builder().tagNames(List.of("夜", "面具")).build());

    assertThat(assetService.get(id).getTagNames()).containsExactlyInAnyOrder("夜", "面具");
  }

  @Test
  void updateCharacterIdsReplacesRelations() {
    Long id = persistAsset("linked").getId();
    CharacterProfile a =
        characterService.create(
            new CharacterCreateRequest("甲", null, null, null, null, null, null, null, null));
    CharacterProfile b =
        characterService.create(
            new CharacterCreateRequest("乙", null, null, null, null, null, null, null, null));
    assetService.update(
        id, AssetUpdateRequest.builder().characterIds(List.of(a.getId())).build());

    assetService.update(
        id, AssetUpdateRequest.builder().characterIds(List.of(b.getId(), a.getId())).build());

    assertThat(assetService.get(id).getCharacterIds())
        .containsExactlyInAnyOrder(a.getId(), b.getId());
  }

  private Asset persistAsset(String name) {
    AssetCategory category = new AssetCategory();
    category.setCode("tag-" + name);
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
    return assetRepository.save(asset);
  }
}
