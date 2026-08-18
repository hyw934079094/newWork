package com.story.admin.service;

import static com.story.admin.domain.AssetStatus.DELETED;
import static com.story.admin.domain.AssetStatus.NORMAL;
import static org.assertj.core.api.Assertions.assertThat;

import com.story.admin.domain.Asset;
import com.story.admin.domain.AssetAssociationSnapshot;
import com.story.admin.domain.AssetCategory;
import com.story.admin.domain.CharacterProfile;
import com.story.admin.domain.StoryArc;
import com.story.admin.domain.StorySeries;
import com.story.admin.dto.AiReferenceItemRequest;
import com.story.admin.dto.ArcCreateRequest;
import com.story.admin.dto.AssetUpdateRequest;
import com.story.admin.dto.CharacterCreateRequest;
import com.story.admin.dto.SeriesCreateRequest;
import com.story.admin.repository.AiReferenceItemRepository;
import com.story.admin.repository.AssetAssociationSnapshotRepository;
import com.story.admin.repository.AssetCategoryRepository;
import com.story.admin.repository.AssetCharacterRelRepository;
import com.story.admin.repository.AssetRepository;
import com.story.admin.repository.StoryArcRepository;
import com.story.admin.repository.StorySeriesRepository;
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
      "spring.datasource.url=jdbc:h2:mem:story_admin_asset_recycle_association_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
      "story.storage.root=../storage"
    })
class AssetRecycleAssociationTest {

  @Autowired AssetService assetService;
  @Autowired CharacterService characterService;
  @Autowired SeriesService seriesService;
  @Autowired ArcService arcService;
  @Autowired AiReferenceService aiReferenceService;
  @Autowired AssetRepository assetRepository;
  @Autowired AssetCategoryRepository categoryRepository;
  @Autowired AssetAssociationSnapshotRepository snapshotRepository;
  @Autowired AssetCharacterRelRepository characterRelRepository;
  @Autowired AiReferenceItemRepository aiReferenceItemRepository;
  @Autowired StorySeriesRepository storySeriesRepository;
  @Autowired StoryArcRepository storyArcRepository;

  @Test
  void recycleDetachesCharacterAndRestoreReattaches() {
    Long assetId = persistAsset("recycle-char").getId();
    CharacterProfile character =
        characterService.create(
            new CharacterCreateRequest(
                "回收角色", null, null, null, null, null, null, null, null, null, null, null));
    assetService.update(
        assetId, AssetUpdateRequest.builder().characterIds(List.of(character.getId())).build());
    assertThat(assetService.get(assetId).getCharacterIds()).containsExactly(character.getId());

    Asset recycled = assetService.recycle(assetId);

    assertThat(recycled.getStatus()).isEqualTo(DELETED);
    assertThat(recycled.getCharacterIds()).isEmpty();
    assertThat(characterRelRepository.findCharacterIdsByAssetId(assetId)).isEmpty();
    List<AssetAssociationSnapshot> snaps = snapshotRepository.findByAssetIdOrderByIdAsc(assetId);
    assertThat(snaps).extracting(AssetAssociationSnapshot::getKind).contains("CHARACTER_REL");
    assertThat(snaps)
        .filteredOn(s -> "CHARACTER_REL".equals(s.getKind()))
        .extracting(AssetAssociationSnapshot::getPayloadJson)
        .anyMatch(json -> json.contains("\"characterId\":" + character.getId()));

    Asset restored = assetService.restore(assetId);

    assertThat(restored.getStatus()).isEqualTo(NORMAL);
    assertThat(restored.getDeletedAt()).isNull();
    assertThat(restored.getCharacterIds()).containsExactly(character.getId());
  }

  @Test
  void recycleClearsSeriesAndArcCoverThenRestore() {
    Long assetId = persistAsset("recycle-covers").getId();
    StorySeries series =
        seriesService.create(new SeriesCreateRequest("回收封面系列", null, null, null, assetId));
    StoryArc arc =
        arcService.create(series.getId(), new ArcCreateRequest("回收封面篇章", null, null, assetId));
    assertThat(storySeriesRepository.findById(series.getId()).orElseThrow().getCoverAssetId())
        .isEqualTo(assetId);
    assertThat(storyArcRepository.findById(arc.getId()).orElseThrow().getCoverAssetId())
        .isEqualTo(assetId);

    Asset recycled = assetService.recycle(assetId);

    assertThat(recycled.getStatus()).isEqualTo(DELETED);
    assertThat(storySeriesRepository.findById(series.getId()).orElseThrow().getCoverAssetId())
        .isNull();
    assertThat(storyArcRepository.findById(arc.getId()).orElseThrow().getCoverAssetId()).isNull();
    List<AssetAssociationSnapshot> snaps = snapshotRepository.findByAssetIdOrderByIdAsc(assetId);
    assertThat(snaps)
        .extracting(AssetAssociationSnapshot::getKind)
        .contains("SERIES_COVER", "ARC_COVER");

    Asset restored = assetService.restore(assetId);

    assertThat(restored.getStatus()).isEqualTo(NORMAL);
    assertThat(storySeriesRepository.findById(series.getId()).orElseThrow().getCoverAssetId())
        .isEqualTo(assetId);
    assertThat(storyArcRepository.findById(arc.getId()).orElseThrow().getCoverAssetId())
        .isEqualTo(assetId);
  }

  @Test
  void restoreSkipsDeletedCharacterKeepsOtherRels() {
    Long assetId = persistAsset("recycle-skip-char").getId();
    CharacterProfile keep =
        characterService.create(
            new CharacterCreateRequest(
                "保留角色", null, null, null, null, null, null, null, null, null, null, null));
    CharacterProfile gone =
        characterService.create(
            new CharacterCreateRequest(
                "将删角色", null, null, null, null, null, null, null, null, null, null, null));
    assetService.update(
        assetId,
        AssetUpdateRequest.builder().characterIds(List.of(keep.getId(), gone.getId())).build());
    StorySeries series =
        seriesService.create(new SeriesCreateRequest("跳过角色封面系列", null, null, null, assetId));

    Asset recycled = assetService.recycle(assetId);

    assertThat(recycled.getStatus()).isEqualTo(DELETED);
    assertThat(recycled.getCharacterIds()).isEmpty();
    assertThat(characterRelRepository.findCharacterIdsByAssetId(assetId)).isEmpty();
    assertThat(storySeriesRepository.findById(series.getId()).orElseThrow().getCoverAssetId())
        .isNull();
    assertThat(snapshotRepository.findByAssetIdOrderByIdAsc(assetId))
        .extracting(AssetAssociationSnapshot::getKind)
        .contains("CHARACTER_REL", "SERIES_COVER");

    characterService.delete(gone.getId());

    Asset restored = assetService.restore(assetId);

    assertThat(restored.getStatus()).isEqualTo(NORMAL);
    assertThat(restored.getCharacterIds()).containsExactly(keep.getId());
    assertThat(storySeriesRepository.findById(series.getId()).orElseThrow().getCoverAssetId())
        .isEqualTo(assetId);
  }

  @Test
  void recycleTwiceKeepsSnapshotsAndRestoreReattaches() {
    Long assetId = persistAsset("recycle-twice").getId();
    CharacterProfile character =
        characterService.create(
            new CharacterCreateRequest(
                "二次回收角色", null, null, null, null, null, null, null, null, null, null, null));
    assetService.update(
        assetId, AssetUpdateRequest.builder().characterIds(List.of(character.getId())).build());

    assetService.recycle(assetId);
    List<AssetAssociationSnapshot> afterFirst =
        snapshotRepository.findByAssetIdOrderByIdAsc(assetId);
    assertThat(afterFirst).extracting(AssetAssociationSnapshot::getKind).contains("CHARACTER_REL");
    String payload = afterFirst.get(0).getPayloadJson();

    Asset recycledAgain = assetService.recycle(assetId);

    assertThat(recycledAgain.getStatus()).isEqualTo(DELETED);
    List<AssetAssociationSnapshot> afterSecond =
        snapshotRepository.findByAssetIdOrderByIdAsc(assetId);
    assertThat(afterSecond).hasSize(afterFirst.size());
    assertThat(afterSecond).extracting(AssetAssociationSnapshot::getKind).contains("CHARACTER_REL");
    assertThat(afterSecond.get(0).getPayloadJson()).isEqualTo(payload);

    Asset restored = assetService.restore(assetId);
    assertThat(restored.getStatus()).isEqualTo(NORMAL);
    assertThat(restored.getCharacterIds()).containsExactly(character.getId());
  }

  @Test
  void restoreTwiceDoesNotDuplicateAiRefs() {
    Long assetId = persistAsset("restore-twice-ai").getId();
    aiReferenceService.replaceCurrentItems(
        List.of(new AiReferenceItemRequest(assetId, "外貌", null, null)));
    assertThat(aiReferenceItemRepository.countByAssetId(assetId)).isEqualTo(1);

    assetService.recycle(assetId);
    assertThat(aiReferenceItemRepository.countByAssetId(assetId)).isZero();
    assertThat(snapshotRepository.findByAssetIdOrderByIdAsc(assetId))
        .extracting(AssetAssociationSnapshot::getKind)
        .contains("AI_REF");

    assetService.restore(assetId);
    assertThat(aiReferenceItemRepository.countByAssetId(assetId)).isEqualTo(1);

    Asset restoredAgain = assetService.restore(assetId);

    assertThat(restoredAgain.getStatus()).isEqualTo(NORMAL);
    assertThat(aiReferenceItemRepository.countByAssetId(assetId)).isEqualTo(1);
  }

  private Asset persistAsset(String name) {
    AssetCategory category = new AssetCategory();
    category.setCode("rec-" + name);
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
