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
      "spring.datasource.url=jdbc:h2:mem:story_admin_asset_list_filter_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
      "story.storage.root=../storage"
    })
class AssetListFilterTest {

  @Autowired AssetService assetService;
  @Autowired CharacterService characterService;
  @Autowired AssetRepository assetRepository;
  @Autowired AssetCategoryRepository categoryRepository;

  @Test
  void listUnlinkedExcludesCharacterLinkedAssets() {
    AssetCategory category = persistCategory("filter-unlinked", "筛选未关联");
    Asset unlinked = persistAsset(category.getId(), "无关联素材");
    Asset linked = persistAsset(category.getId(), "已关联素材");
    CharacterProfile character =
        characterService.create(
            new CharacterCreateRequest(
                "筛选角色", null, null, null, null, null, null, null, null, null, null));
    assetService.update(
        linked.getId(),
        AssetUpdateRequest.builder().characterIds(List.of(character.getId())).build());

    List<Asset> result =
        assetService.list(category.getId(), "NORMAL", null, "unlinked", null);

    assertThat(result).extracting(Asset::getId).containsExactly(unlinked.getId());
  }

  @Test
  void listByCharacterIdReturnsOnlyLinked() {
    AssetCategory category = persistCategory("filter-by-char", "筛选人物");
    Asset unlinked = persistAsset(category.getId(), "未挂人物");
    Asset linkedA = persistAsset(category.getId(), "挂甲");
    Asset linkedB = persistAsset(category.getId(), "挂乙");
    CharacterProfile a =
        characterService.create(
            new CharacterCreateRequest("甲", null, null, null, null, null, null, null, null, null, null));
    CharacterProfile b =
        characterService.create(
            new CharacterCreateRequest("乙", null, null, null, null, null, null, null, null, null, null));
    assetService.update(
        linkedA.getId(), AssetUpdateRequest.builder().characterIds(List.of(a.getId())).build());
    assetService.update(
        linkedB.getId(), AssetUpdateRequest.builder().characterIds(List.of(b.getId())).build());

    List<Asset> result =
        assetService.list(category.getId(), "NORMAL", null, null, a.getId());

    assertThat(result).extracting(Asset::getId).containsExactly(linkedA.getId());
    assertThat(result).extracting(Asset::getId).doesNotContain(unlinked.getId(), linkedB.getId());
  }

  private AssetCategory persistCategory(String code, String name) {
    AssetCategory category = new AssetCategory();
    category.setCode(code);
    category.setName(name);
    category.setSortOrder(1);
    category.setSystemPreset(false);
    return categoryRepository.save(category);
  }

  private Asset persistAsset(Long categoryId, String name) {
    Asset asset = new Asset();
    asset.setDisplayName(name);
    asset.setCategoryId(categoryId);
    asset.setSortOrder(0);
    asset.setStatus(NORMAL);
    asset.setStoragePath("assets/test/" + name + ".png");
    return assetRepository.save(asset);
  }
}
