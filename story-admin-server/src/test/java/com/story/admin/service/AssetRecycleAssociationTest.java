package com.story.admin.service;

import static com.story.admin.domain.AssetStatus.DELETED;
import static com.story.admin.domain.AssetStatus.NORMAL;
import static org.assertj.core.api.Assertions.assertThat;

import com.story.admin.domain.Asset;
import com.story.admin.domain.AssetAssociationSnapshot;
import com.story.admin.domain.AssetCategory;
import com.story.admin.domain.AssetComboMember;
import com.story.admin.domain.CharacterProfile;
import com.story.admin.domain.PageAssetRef;
import com.story.admin.domain.StoryArc;
import com.story.admin.domain.StoryPage;
import com.story.admin.domain.StorySeries;
import com.story.admin.dto.AiReferenceItemRequest;
import com.story.admin.dto.ArcCreateRequest;
import com.story.admin.dto.AssetUpdateRequest;
import com.story.admin.dto.CharacterCreateRequest;
import com.story.admin.dto.ComboMemberRequest;
import com.story.admin.dto.ComboUpsertRequest;
import com.story.admin.dto.PageCreateRequest;
import com.story.admin.dto.PageUpdateRequest;
import com.story.admin.dto.SeriesCreateRequest;
import com.story.admin.repository.AiReferenceItemRepository;
import com.story.admin.repository.AssetAssociationSnapshotRepository;
import com.story.admin.repository.AssetCategoryRepository;
import com.story.admin.repository.AssetCharacterRelRepository;
import com.story.admin.repository.AssetComboMemberRepository;
import com.story.admin.repository.AssetComboRepository;
import com.story.admin.repository.AssetRepository;
import com.story.admin.repository.PageAssetRefRepository;
import com.story.admin.repository.PageComboRefRepository;
import com.story.admin.repository.StoryArcRepository;
import com.story.admin.repository.StoryPageRepository;
import com.story.admin.repository.StorySeriesRepository;
import java.math.BigDecimal;
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
  @Autowired PageService pageService;
  @Autowired ComboService comboService;
  @Autowired AiReferenceService aiReferenceService;
  @Autowired AssetRepository assetRepository;
  @Autowired AssetCategoryRepository categoryRepository;
  @Autowired AssetAssociationSnapshotRepository snapshotRepository;
  @Autowired AssetCharacterRelRepository characterRelRepository;
  @Autowired AiReferenceItemRepository aiReferenceItemRepository;
  @Autowired StorySeriesRepository storySeriesRepository;
  @Autowired StoryArcRepository storyArcRepository;
  @Autowired AssetComboRepository comboRepository;
  @Autowired AssetComboMemberRepository comboMemberRepository;
  @Autowired StoryPageRepository storyPageRepository;
  @Autowired PageAssetRefRepository pageAssetRefRepository;
  @Autowired PageComboRefRepository pageComboRefRepository;

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

  @Test
  void recycleRemovesComboMemberRewritesPlaySequenceAndRestore() {
    Long a1 = persistAsset("combo-keep-1").getId();
    Long a2 = persistAsset("combo-recycle-mid").getId();
    Long a3 = persistAsset("combo-keep-3").getId();
    var combo =
        comboService.create(
            new ComboUpsertRequest(
                "recycle-mid-member",
                "1,2,3,2",
                new BigDecimal("1.0"),
                true,
                null,
                List.of(
                    new ComboMemberRequest(a1, 1),
                    new ComboMemberRequest(a2, 2),
                    new ComboMemberRequest(a3, 3)),
                List.of()));

    Asset recycled = assetService.recycle(a2);

    assertThat(recycled.getStatus()).isEqualTo(DELETED);
    List<AssetComboMember> remaining =
        comboMemberRepository.findByComboIdOrderBySortOrderAscMemberNoAsc(combo.id());
    assertThat(remaining).extracting(AssetComboMember::getAssetId).containsExactly(a1, a3);
    assertThat(remaining).extracting(AssetComboMember::getMemberNo).containsExactly(1, 3);
    assertThat(comboRepository.findById(combo.id()).orElseThrow().getPlaySequence())
        .isEqualTo("1,3");
    List<AssetAssociationSnapshot> snaps = snapshotRepository.findByAssetIdOrderByIdAsc(a2);
    assertThat(snaps).extracting(AssetAssociationSnapshot::getKind).contains("COMBO_MEMBER");
    assertThat(snaps)
        .filteredOn(s -> "COMBO_MEMBER".equals(s.getKind()))
        .extracting(AssetAssociationSnapshot::getPayloadJson)
        .anyMatch(
            json ->
                json.contains("\"comboId\":" + combo.id())
                    && json.contains("\"memberNo\":2")
                    && json.contains("\"sortOrder\":1")
                    && json.contains("\"playSequenceBefore\":\"1,2,3,2\""));

    Asset restored = assetService.restore(a2);

    assertThat(restored.getStatus()).isEqualTo(NORMAL);
    List<AssetComboMember> afterRestore =
        comboMemberRepository.findByComboIdOrderBySortOrderAscMemberNoAsc(combo.id());
    assertThat(afterRestore)
        .extracting(AssetComboMember::getAssetId)
        .containsExactly(a1, a2, a3);
    assertThat(afterRestore).extracting(AssetComboMember::getMemberNo).containsExactly(1, 2, 3);
    assertThat(comboRepository.findById(combo.id()).orElseThrow().getPlaySequence())
        .isEqualTo("1,2,3,2");
  }

  @Test
  void recycleLastComboMemberLeavesEmptyCombo() {
    Long assetId = persistAsset("combo-last-member").getId();
    var combo =
        comboService.create(
            new ComboUpsertRequest(
                "recycle-last-member",
                "1",
                new BigDecimal("1.0"),
                true,
                null,
                List.of(new ComboMemberRequest(assetId, 1)),
                List.of()));
    Long arcId = persistArc();
    StoryPage page = pageService.create(arcId, new PageCreateRequest("combo-page"));
    String json =
        "[{\"type\":\"BEAT\",\"children\":[{\"type\":\"COMBO\",\"comboId\":"
            + combo.id()
            + "}]}]";
    pageService.update(page.getId(), new PageUpdateRequest("combo-page", json));
    assertThat(pageComboRefRepository.existsByComboId(combo.id())).isTrue();
    assertThat(pageAssetRefRepository.findByAssetId(assetId))
        .extracting(PageAssetRef::getRefKind)
        .contains("BEAT_COMBO_MEMBER");

    Asset recycled = assetService.recycle(assetId);

    assertThat(recycled.getStatus()).isEqualTo(DELETED);
    assertThat(comboMemberRepository.findByComboIdOrderBySortOrderAscMemberNoAsc(combo.id()))
        .isEmpty();
    assertThat(comboRepository.findById(combo.id()).orElseThrow().getPlaySequence()).isEmpty();
    StoryPage afterRecycle = storyPageRepository.findById(page.getId()).orElseThrow();
    assertThat(afterRecycle.getContentJson()).contains("\"coverAssetId\":null");
    assertThat(afterRecycle.getContentJson()).contains("\"comboId\":" + combo.id());
    assertThat(pageComboRefRepository.existsByComboId(combo.id())).isTrue();
    assertThat(pageAssetRefRepository.findByAssetId(assetId)).isEmpty();
    List<AssetAssociationSnapshot> snaps =
        snapshotRepository.findByAssetIdOrderByIdAsc(assetId);
    assertThat(snaps)
        .extracting(AssetAssociationSnapshot::getKind)
        .contains("COMBO_MEMBER", "PAGE_COMBO_MEMBER_REF");
    assertThat(snaps)
        .filteredOn(s -> "PAGE_COMBO_MEMBER_REF".equals(s.getKind()))
        .extracting(AssetAssociationSnapshot::getPayloadJson)
        .anyMatch(
            payload ->
                payload.contains("\"pageId\":" + page.getId())
                    && payload.contains("\"comboId\":" + combo.id())
                    && payload.contains("\"refKind\":\"BEAT_COMBO_MEMBER\""));

    Asset restored = assetService.restore(assetId);

    assertThat(restored.getStatus()).isEqualTo(NORMAL);
    List<AssetComboMember> members =
        comboMemberRepository.findByComboIdOrderBySortOrderAscMemberNoAsc(combo.id());
    assertThat(members).extracting(AssetComboMember::getAssetId).containsExactly(assetId);
    assertThat(members).extracting(AssetComboMember::getMemberNo).containsExactly(1);
    assertThat(comboRepository.findById(combo.id()).orElseThrow().getPlaySequence()).isEqualTo("1");
    StoryPage afterRestore = storyPageRepository.findById(page.getId()).orElseThrow();
    assertThat(afterRestore.getContentJson()).contains("\"coverAssetId\":" + assetId);
    assertThat(pageAssetRefRepository.findByAssetId(assetId))
        .extracting(PageAssetRef::getRefKind)
        .contains("BEAT_COMBO_MEMBER");
  }

  @Test
  void recycleThenHardDeleteClearsSnapshots() {
    Long assetId = persistAsset("hard-delete-snaps").getId();
    CharacterProfile character =
        characterService.create(
            new CharacterCreateRequest(
                "硬删快照角色", null, null, null, null, null, null, null, null, null, null, null));
    assetService.update(
        assetId, AssetUpdateRequest.builder().characterIds(List.of(character.getId())).build());

    assetService.recycle(assetId);
    assertThat(snapshotRepository.findByAssetIdOrderByIdAsc(assetId)).isNotEmpty();
    assertThat(characterRelRepository.findCharacterIdsByAssetId(assetId)).isEmpty();

    assetService.hardDelete(assetId);

    assertThat(assetRepository.findById(assetId)).isEmpty();
    assertThat(snapshotRepository.findByAssetIdOrderByIdAsc(assetId)).isEmpty();
    assertThat(characterRelRepository.findCharacterIdsByAssetId(assetId)).isEmpty();
  }

  @Test
  void recycleClearsPageBeatCoverKeepsNodeAndRestoreFills() {
    Long assetId = persistAsset("page-beat-cover").getId();
    Long arcId = persistArc();
    StoryPage page = pageService.create(arcId, new PageCreateRequest("cover-page"));
    String json =
        "[{\"type\":\"BEAT\",\"coverAssetId\":"
            + assetId
            + ",\"children\":[{\"type\":\"BODY\",\"text\":\"hi\"}]}]";
    pageService.update(page.getId(), new PageUpdateRequest("cover-page", json));
    assertThat(pageAssetRefRepository.findByAssetId(assetId))
        .extracting(PageAssetRef::getRefKind)
        .containsExactly("BEAT_COVER");

    Asset recycled = assetService.recycle(assetId);

    assertThat(recycled.getStatus()).isEqualTo(DELETED);
    StoryPage afterRecycle = storyPageRepository.findById(page.getId()).orElseThrow();
    assertThat(afterRecycle.getContentJson()).contains("\"type\":\"COVER\"");
    assertThat(afterRecycle.getContentJson()).doesNotContain("\"assetId\":" + assetId);
    assertThat(afterRecycle.getContentJson()).contains("\"coverAssetId\":null");
    assertThat(pageAssetRefRepository.findByAssetId(assetId)).isEmpty();
    List<AssetAssociationSnapshot> snaps =
        snapshotRepository.findByAssetIdOrderByIdAsc(assetId);
    assertThat(snaps).extracting(AssetAssociationSnapshot::getKind).contains("PAGE_BEAT_COVER");
    assertThat(snaps)
        .filteredOn(s -> "PAGE_BEAT_COVER".equals(s.getKind()))
        .extracting(AssetAssociationSnapshot::getPayloadJson)
        .anyMatch(
            payload ->
                payload.contains("\"pageId\":" + page.getId())
                    && payload.contains("\"beatIndex\":0")
                    && payload.contains("\"childIndex\":0"));

    Asset restored = assetService.restore(assetId);

    assertThat(restored.getStatus()).isEqualTo(NORMAL);
    StoryPage afterRestore = storyPageRepository.findById(page.getId()).orElseThrow();
    assertThat(afterRestore.getContentJson()).contains("\"assetId\":" + assetId);
    assertThat(afterRestore.getContentJson()).contains("\"coverAssetId\":" + assetId);
    assertThat(pageAssetRefRepository.findByAssetId(assetId))
        .extracting(PageAssetRef::getRefKind)
        .containsExactly("BEAT_COVER");
  }

  @Test
  void recycleSamePageCoverAndComboFirstFrameClearsBothAndRestoreReattaches() {
    Long assetId = persistAsset("shared-cover-combo").getId();
    Long extra = persistAsset("combo-other-frame").getId();
    var combo =
        comboService.create(
            new ComboUpsertRequest(
                "shared-first-frame",
                "1,2",
                new BigDecimal("1.0"),
                true,
                null,
                List.of(new ComboMemberRequest(assetId, 1), new ComboMemberRequest(extra, 2)),
                List.of()));
    Long arcId = persistArc();
    StoryPage page = pageService.create(arcId, new PageCreateRequest("shared-page"));
    String json =
        "[{\"type\":\"BEAT\",\"coverAssetId\":"
            + assetId
            + ",\"children\":[{\"type\":\"COVER\",\"assetId\":"
            + assetId
            + "}]},"
            + "{\"type\":\"BEAT\",\"children\":[{\"type\":\"COMBO\",\"comboId\":"
            + combo.id()
            + "}]}]";
    pageService.update(page.getId(), new PageUpdateRequest("shared-page", json));
    assertThat(pageAssetRefRepository.findByAssetId(assetId))
        .extracting(PageAssetRef::getRefKind)
        .contains("BEAT_COVER", "BEAT_COMBO_MEMBER");

    Asset recycled = assetService.recycle(assetId);

    assertThat(recycled.getStatus()).isEqualTo(DELETED);
    StoryPage afterRecycle = storyPageRepository.findById(page.getId()).orElseThrow();
    assertThat(afterRecycle.getContentJson()).contains("\"type\":\"COVER\"");
    assertThat(afterRecycle.getContentJson()).doesNotContain("\"assetId\":" + assetId);
    assertThat(afterRecycle.getContentJson()).doesNotContain("\"coverAssetId\":" + assetId);
    assertThat(afterRecycle.getContentJson()).contains("\"comboId\":" + combo.id());
    assertThat(comboMemberRepository.findByComboIdOrderBySortOrderAscMemberNoAsc(combo.id()))
        .extracting(AssetComboMember::getAssetId)
        .containsExactly(extra);
    assertThat(pageAssetRefRepository.findByAssetId(assetId)).isEmpty();
    List<AssetAssociationSnapshot> snaps = snapshotRepository.findByAssetIdOrderByIdAsc(assetId);
    assertThat(snaps)
        .extracting(AssetAssociationSnapshot::getKind)
        .contains("COMBO_MEMBER", "PAGE_BEAT_COVER", "PAGE_COMBO_MEMBER_REF");
    assertThat(snaps)
        .filteredOn(s -> "PAGE_BEAT_COVER".equals(s.getKind()))
        .extracting(AssetAssociationSnapshot::getPayloadJson)
        .anyMatch(
            payload ->
                payload.contains("\"pageId\":" + page.getId())
                    && payload.contains("\"beatIndex\":0"));

    Asset restored = assetService.restore(assetId);

    assertThat(restored.getStatus()).isEqualTo(NORMAL);
    StoryPage afterRestore = storyPageRepository.findById(page.getId()).orElseThrow();
    assertThat(afterRestore.getContentJson()).contains("\"type\":\"COVER\"");
    assertThat(afterRestore.getContentJson()).contains("\"assetId\":" + assetId);
    assertThat(afterRestore.getContentJson()).contains("\"coverAssetId\":" + assetId);
    assertThat(comboMemberRepository.findByComboIdOrderBySortOrderAscMemberNoAsc(combo.id()))
        .extracting(AssetComboMember::getAssetId)
        .containsExactly(assetId, extra);
    assertThat(pageAssetRefRepository.findByAssetId(assetId))
        .extracting(PageAssetRef::getRefKind)
        .contains("BEAT_COVER", "BEAT_COMBO_MEMBER");
  }

  private Long persistArc() {
    Long seriesId =
        seriesService.create(new SeriesCreateRequest("回收页系列", null, null, null, null)).getId();
    StoryArc arc = arcService.create(seriesId, new ArcCreateRequest("回收页篇章", null, null, null));
    return arc.getId();
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
