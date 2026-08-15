package com.story.admin.service;

import static com.story.admin.domain.AssetStatus.NORMAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.story.admin.domain.Asset;
import com.story.admin.domain.AssetCategory;
import com.story.admin.domain.AssetCharacterRel;
import com.story.admin.domain.AssetCharacterRelId;
import com.story.admin.domain.CharacterProfile;
import com.story.admin.dto.CharacterCreateRequest;
import com.story.admin.repository.AssetCategoryRepository;
import com.story.admin.repository.AssetCharacterRelRepository;
import com.story.admin.repository.AssetRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.datasource.url=jdbc:h2:mem:story_admin_asset_scope_reorder_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
      "story.storage.root=../storage"
    })
class AssetScopeReorderTest {

  @Autowired AssetService assetService;
  @Autowired CharacterService characterService;
  @Autowired AssetRepository assetRepository;
  @Autowired AssetCategoryRepository categoryRepository;
  @Autowired AssetCharacterRelRepository characterRelRepository;

  @Test
  void listByCharacterUsesRelSortOrderNotAssetSortOrder() {
    Long categoryId = persistCategory("scope-char-order", "人物序分类").getId();
    Asset assetA = persistAsset(categoryId, "A", 0);
    Asset assetB = persistAsset(categoryId, "B", 1);
    CharacterProfile character =
        characterService.create(
            new CharacterCreateRequest(
                "序角色", null, null, null, null, null, null, null, null, null, null, null));

    characterRelRepository.save(new AssetCharacterRel(assetA.getId(), character.getId(), 10));
    characterRelRepository.save(new AssetCharacterRel(assetB.getId(), character.getId(), 1));

    List<Asset> byCharacter =
        assetService.list(categoryId, "NORMAL", null, null, character.getId());
    assertThat(byCharacter).extracting(Asset::getId).containsExactly(assetB.getId(), assetA.getId());

    List<Asset> byAll = assetService.list(categoryId, "NORMAL", null, "all", null);
    assertThat(byAll).extracting(Asset::getId).containsExactly(assetA.getId(), assetB.getId());
  }

  @Test
  void reorderByCharacterDoesNotChangeAssetSortOrder() {
    Long categoryId = persistCategory("scope-reorder-char", "人物改序分类").getId();
    Asset assetA = persistAsset(categoryId, "A", 0);
    Asset assetB = persistAsset(categoryId, "B", 1);
    Asset assetC = persistAsset(categoryId, "C", 2);
    CharacterProfile character =
        characterService.create(
            new CharacterCreateRequest(
                "改序角色", null, null, null, null, null, null, null, null, null, null, null));

    characterRelRepository.save(new AssetCharacterRel(assetA.getId(), character.getId(), 0));
    characterRelRepository.save(new AssetCharacterRel(assetB.getId(), character.getId(), 1));
    characterRelRepository.save(new AssetCharacterRel(assetC.getId(), character.getId(), 2));

    List<Long> reversed = List.of(assetC.getId(), assetB.getId(), assetA.getId());
    assetService.reorderByScope(categoryId, "CHARACTER", character.getId(), reversed);

    assertThat(
            characterRelRepository
                .findById(new AssetCharacterRelId(assetC.getId(), character.getId()))
                .orElseThrow()
                .getSortOrder())
        .isEqualTo(0);
    assertThat(
            characterRelRepository
                .findById(new AssetCharacterRelId(assetB.getId(), character.getId()))
                .orElseThrow()
                .getSortOrder())
        .isEqualTo(1);
    assertThat(
            characterRelRepository
                .findById(new AssetCharacterRelId(assetA.getId(), character.getId()))
                .orElseThrow()
                .getSortOrder())
        .isEqualTo(2);

    assertThat(assetRepository.findById(assetA.getId()).orElseThrow().getSortOrder()).isEqualTo(0);
    assertThat(assetRepository.findById(assetB.getId()).orElseThrow().getSortOrder()).isEqualTo(1);
    assertThat(assetRepository.findById(assetC.getId()).orElseThrow().getSortOrder()).isEqualTo(2);

    List<Asset> byCharacter =
        assetService.list(categoryId, "NORMAL", null, null, character.getId());
    assertThat(byCharacter)
        .extracting(Asset::getId)
        .containsExactly(assetC.getId(), assetB.getId(), assetA.getId());

    List<Asset> byAll = assetService.list(categoryId, "NORMAL", null, "all", null);
    assertThat(byAll)
        .extracting(Asset::getId)
        .containsExactly(assetA.getId(), assetB.getId(), assetC.getId());
  }

  @Test
  void reorderByScopeRejectsMismatchedIds() {
    Long categoryId = persistCategory("scope-reorder-mismatch", "改序校验分类").getId();
    Asset assetA = persistAsset(categoryId, "A", 0);
    Asset assetB = persistAsset(categoryId, "B", 1);
    CharacterProfile character =
        characterService.create(
            new CharacterCreateRequest(
                "校验角色", null, null, null, null, null, null, null, null, null, null, null));

    characterRelRepository.save(new AssetCharacterRel(assetA.getId(), character.getId(), 0));
    characterRelRepository.save(new AssetCharacterRel(assetB.getId(), character.getId(), 1));

    assertThatThrownBy(
            () ->
                assetService.reorderByScope(
                    categoryId, "CHARACTER", character.getId(), List.of(assetA.getId())))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            ex -> {
              ResponseStatusException rse = (ResponseStatusException) ex;
              assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            });
  }

  private AssetCategory persistCategory(String code, String name) {
    AssetCategory category = new AssetCategory();
    category.setCode(code);
    category.setName(name);
    category.setSortOrder(1);
    category.setSystemPreset(false);
    return categoryRepository.save(category);
  }

  private Asset persistAsset(Long categoryId, String name, int sortOrder) {
    Asset asset = new Asset();
    asset.setDisplayName(name);
    asset.setCategoryId(categoryId);
    asset.setSortOrder(sortOrder);
    asset.setStatus(NORMAL);
    asset.setStoragePath("assets/test/" + name + ".png");
    return assetRepository.save(asset);
  }
}
