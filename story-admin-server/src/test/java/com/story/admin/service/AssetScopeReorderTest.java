package com.story.admin.service;

import static com.story.admin.domain.AssetStatus.NORMAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.story.admin.domain.Asset;
import com.story.admin.domain.AssetArcRel;
import com.story.admin.domain.AssetArcRelId;
import com.story.admin.domain.AssetCategory;
import com.story.admin.domain.AssetCharacterRel;
import com.story.admin.domain.AssetCharacterRelId;
import com.story.admin.domain.AssetLinkType;
import com.story.admin.domain.AssetSeriesRel;
import com.story.admin.domain.AssetSeriesRelId;
import com.story.admin.domain.AssetUnlinkedOrder;
import com.story.admin.domain.AssetUnlinkedOrderId;
import com.story.admin.domain.CharacterProfile;
import com.story.admin.domain.StoryArc;
import com.story.admin.domain.StorySeries;
import com.story.admin.dto.ArcCreateRequest;
import com.story.admin.dto.AssetUpdateRequest;
import com.story.admin.dto.CharacterCreateRequest;
import com.story.admin.dto.SeriesCreateRequest;
import com.story.admin.repository.AssetArcRelRepository;
import com.story.admin.repository.AssetCategoryRepository;
import com.story.admin.repository.AssetCharacterRelRepository;
import com.story.admin.repository.AssetRepository;
import com.story.admin.repository.AssetSeriesRelRepository;
import com.story.admin.repository.AssetUnlinkedOrderRepository;
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
  @Autowired SeriesService seriesService;
  @Autowired ArcService arcService;
  @Autowired AssetRepository assetRepository;
  @Autowired AssetCategoryRepository categoryRepository;
  @Autowired AssetCharacterRelRepository characterRelRepository;
  @Autowired AssetSeriesRelRepository seriesRelRepository;
  @Autowired AssetArcRelRepository arcRelRepository;
  @Autowired AssetUnlinkedOrderRepository unlinkedOrderRepository;

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

  @Test
  void reorderBySeriesDoesNotChangeAssetSortOrder() {
    Long categoryId = persistCategory("scope-reorder-series", "系列改序分类").getId();
    Asset assetA = persistAsset(categoryId, "A", 0);
    Asset assetB = persistAsset(categoryId, "B", 1);
    Asset assetC = persistAsset(categoryId, "C", 2);
    StorySeries series =
        seriesService.create(new SeriesCreateRequest("改序系列", null, null, null, null));

    seriesRelRepository.save(new AssetSeriesRel(assetA.getId(), series.getId(), 0));
    seriesRelRepository.save(new AssetSeriesRel(assetB.getId(), series.getId(), 1));
    seriesRelRepository.save(new AssetSeriesRel(assetC.getId(), series.getId(), 2));

    List<Long> reversed = List.of(assetC.getId(), assetB.getId(), assetA.getId());
    assetService.reorderByScope(categoryId, "SERIES", series.getId(), reversed);

    assertThat(
            seriesRelRepository
                .findById(new AssetSeriesRelId(assetC.getId(), series.getId()))
                .orElseThrow()
                .getSortOrder())
        .isEqualTo(0);
    assertThat(
            seriesRelRepository
                .findById(new AssetSeriesRelId(assetB.getId(), series.getId()))
                .orElseThrow()
                .getSortOrder())
        .isEqualTo(1);
    assertThat(
            seriesRelRepository
                .findById(new AssetSeriesRelId(assetA.getId(), series.getId()))
                .orElseThrow()
                .getSortOrder())
        .isEqualTo(2);

    assertThat(assetRepository.findById(assetA.getId()).orElseThrow().getSortOrder()).isEqualTo(0);
    assertThat(assetRepository.findById(assetB.getId()).orElseThrow().getSortOrder()).isEqualTo(1);
    assertThat(assetRepository.findById(assetC.getId()).orElseThrow().getSortOrder()).isEqualTo(2);

    List<Asset> bySeries =
        assetService.list(categoryId, "NORMAL", null, null, null, "SERIES", series.getId(), null);
    assertThat(bySeries)
        .extracting(Asset::getId)
        .containsExactly(assetC.getId(), assetB.getId(), assetA.getId());

    List<Asset> byAll = assetService.list(categoryId, "NORMAL", null, "all", null);
    assertThat(byAll)
        .extracting(Asset::getId)
        .containsExactly(assetA.getId(), assetB.getId(), assetC.getId());
  }

  @Test
  void reorderByArcDoesNotChangeAssetSortOrder() {
    Long categoryId = persistCategory("scope-reorder-arc", "篇章改序分类").getId();
    Asset assetA = persistAsset(categoryId, "A", 0);
    Asset assetB = persistAsset(categoryId, "B", 1);
    Asset assetC = persistAsset(categoryId, "C", 2);
    StorySeries series =
        seriesService.create(new SeriesCreateRequest("篇章所属系列", null, null, null, null));
    StoryArc arc = arcService.create(series.getId(), new ArcCreateRequest("改序篇章", null, null, null));

    arcRelRepository.save(new AssetArcRel(assetA.getId(), arc.getId(), 0));
    arcRelRepository.save(new AssetArcRel(assetB.getId(), arc.getId(), 1));
    arcRelRepository.save(new AssetArcRel(assetC.getId(), arc.getId(), 2));

    List<Long> reversed = List.of(assetC.getId(), assetB.getId(), assetA.getId());
    assetService.reorderByScope(categoryId, "ARC", arc.getId(), reversed);

    assertThat(
            arcRelRepository
                .findById(new AssetArcRelId(assetC.getId(), arc.getId()))
                .orElseThrow()
                .getSortOrder())
        .isEqualTo(0);
    assertThat(
            arcRelRepository
                .findById(new AssetArcRelId(assetB.getId(), arc.getId()))
                .orElseThrow()
                .getSortOrder())
        .isEqualTo(1);
    assertThat(
            arcRelRepository
                .findById(new AssetArcRelId(assetA.getId(), arc.getId()))
                .orElseThrow()
                .getSortOrder())
        .isEqualTo(2);

    assertThat(assetRepository.findById(assetA.getId()).orElseThrow().getSortOrder()).isEqualTo(0);
    assertThat(assetRepository.findById(assetB.getId()).orElseThrow().getSortOrder()).isEqualTo(1);
    assertThat(assetRepository.findById(assetC.getId()).orElseThrow().getSortOrder()).isEqualTo(2);

    List<Asset> byArc =
        assetService.list(categoryId, "NORMAL", null, null, null, "ARC", null, arc.getId());
    assertThat(byArc)
        .extracting(Asset::getId)
        .containsExactly(assetC.getId(), assetB.getId(), assetA.getId());

    List<Asset> byAll = assetService.list(categoryId, "NORMAL", null, "all", null);
    assertThat(byAll)
        .extracting(Asset::getId)
        .containsExactly(assetA.getId(), assetB.getId(), assetC.getId());
  }

  @Test
  void updateArcLinkPreservesScopeSortOrder() {
    Long categoryId = persistCategory("scope-preserve-arc", "篇章保序分类").getId();
    Asset assetA = persistAsset(categoryId, "A", 0);
    Asset assetB = persistAsset(categoryId, "B", 1);
    Asset assetC = persistAsset(categoryId, "C", 2);
    StorySeries series =
        seriesService.create(new SeriesCreateRequest("保序系列", null, null, null, null));
    StoryArc arc = arcService.create(series.getId(), new ArcCreateRequest("保序篇章", null, null, null));

    arcRelRepository.save(new AssetArcRel(assetA.getId(), arc.getId(), 0));
    arcRelRepository.save(new AssetArcRel(assetB.getId(), arc.getId(), 1));
    arcRelRepository.save(new AssetArcRel(assetC.getId(), arc.getId(), 2));

    assetService.reorderByScope(
        categoryId, "ARC", arc.getId(), List.of(assetC.getId(), assetB.getId(), assetA.getId()));

    assetService.update(
        assetC.getId(),
        AssetUpdateRequest.builder()
            .displayName("C-renamed")
            .linkType(AssetLinkType.ARC)
            .arcIds(List.of(arc.getId()))
            .build());

    List<Asset> byArc =
        assetService.list(categoryId, "NORMAL", null, null, null, "ARC", null, arc.getId());
    assertThat(byArc)
        .extracting(Asset::getId)
        .containsExactly(assetC.getId(), assetB.getId(), assetA.getId());
    assertThat(
            arcRelRepository
                .findById(new AssetArcRelId(assetC.getId(), arc.getId()))
                .orElseThrow()
                .getSortOrder())
        .isEqualTo(0);
  }

  @Test
  void reorderByUnlinkedDoesNotChangeAssetSortOrder() {
    Long categoryId = persistCategory("scope-reorder-unlinked", "无关联改序分类").getId();
    Asset assetA = persistAsset(categoryId, "A", 0);
    Asset assetB = persistAsset(categoryId, "B", 1);
    Asset assetC = persistAsset(categoryId, "C", 2);

    unlinkedOrderRepository.save(new AssetUnlinkedOrder(categoryId, assetA.getId(), 0));
    unlinkedOrderRepository.save(new AssetUnlinkedOrder(categoryId, assetB.getId(), 1));
    unlinkedOrderRepository.save(new AssetUnlinkedOrder(categoryId, assetC.getId(), 2));

    List<Long> reversed = List.of(assetC.getId(), assetB.getId(), assetA.getId());
    assetService.reorderByScope(categoryId, "UNLINKED", null, reversed);

    assertThat(
            unlinkedOrderRepository
                .findById(new AssetUnlinkedOrderId(categoryId, assetC.getId()))
                .orElseThrow()
                .getSortOrder())
        .isEqualTo(0);
    assertThat(
            unlinkedOrderRepository
                .findById(new AssetUnlinkedOrderId(categoryId, assetB.getId()))
                .orElseThrow()
                .getSortOrder())
        .isEqualTo(1);
    assertThat(
            unlinkedOrderRepository
                .findById(new AssetUnlinkedOrderId(categoryId, assetA.getId()))
                .orElseThrow()
                .getSortOrder())
        .isEqualTo(2);

    assertThat(assetRepository.findById(assetA.getId()).orElseThrow().getSortOrder()).isEqualTo(0);
    assertThat(assetRepository.findById(assetB.getId()).orElseThrow().getSortOrder()).isEqualTo(1);
    assertThat(assetRepository.findById(assetC.getId()).orElseThrow().getSortOrder()).isEqualTo(2);

    List<Asset> byUnlinked = assetService.list(categoryId, "NORMAL", null, "unlinked", null);
    assertThat(byUnlinked)
        .extracting(Asset::getId)
        .containsExactly(assetC.getId(), assetB.getId(), assetA.getId());

    List<Asset> byAll = assetService.list(categoryId, "NORMAL", null, "all", null);
    assertThat(byAll)
        .extracting(Asset::getId)
        .containsExactly(assetA.getId(), assetB.getId(), assetC.getId());
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
