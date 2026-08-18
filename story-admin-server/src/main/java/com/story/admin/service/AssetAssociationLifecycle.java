package com.story.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.story.admin.domain.AiReferenceItem;
import com.story.admin.domain.AssetArcRel;
import com.story.admin.domain.AssetAssociationSnapshot;
import com.story.admin.domain.AssetCharacterRel;
import com.story.admin.domain.AssetCombo;
import com.story.admin.domain.AssetComboMember;
import com.story.admin.domain.AssetSeriesRel;
import com.story.admin.domain.IdentityAssetRel;
import com.story.admin.domain.PageAssetRef;
import com.story.admin.domain.PageAssetRefId;
import com.story.admin.domain.PageComboRef;
import com.story.admin.domain.StoryArc;
import com.story.admin.domain.StoryPage;
import com.story.admin.domain.StorySeries;
import com.story.admin.repository.AiReferenceItemRepository;
import com.story.admin.repository.AiReferenceSessionRepository;
import com.story.admin.repository.AssetArcRelRepository;
import com.story.admin.repository.AssetAssociationSnapshotRepository;
import com.story.admin.repository.AssetCharacterRelRepository;
import com.story.admin.repository.AssetComboMemberRepository;
import com.story.admin.repository.AssetComboRepository;
import com.story.admin.repository.AssetSeriesRelRepository;
import com.story.admin.repository.CharacterIdentityRepository;
import com.story.admin.repository.CharacterProfileRepository;
import com.story.admin.repository.IdentityAssetRelRepository;
import com.story.admin.repository.PageAssetRefRepository;
import com.story.admin.repository.PageComboRefRepository;
import com.story.admin.repository.StoryArcRepository;
import com.story.admin.repository.StoryPageRepository;
import com.story.admin.repository.StorySeriesRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetAssociationLifecycle {

  static final String CHARACTER_REL = "CHARACTER_REL";
  static final String SERIES_REL = "SERIES_REL";
  static final String ARC_REL = "ARC_REL";
  static final String IDENTITY_REL = "IDENTITY_REL";
  static final String AI_REF = "AI_REF";
  static final String SERIES_COVER = "SERIES_COVER";
  static final String ARC_COVER = "ARC_COVER";
  static final String COMBO_MEMBER = "COMBO_MEMBER";
  static final String PAGE_BEAT_COVER = "PAGE_BEAT_COVER";
  static final String PAGE_COMBO_MEMBER_REF = "PAGE_COMBO_MEMBER_REF";
  static final String REF_KIND_BEAT_COVER = "BEAT_COVER";
  static final String REF_KIND_BEAT_COMBO_MEMBER = "BEAT_COMBO_MEMBER";

  private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {};

  private final AssetAssociationSnapshotRepository snapshotRepository;
  private final AssetCharacterRelRepository characterRelRepository;
  private final AssetSeriesRelRepository assetSeriesRelRepository;
  private final AssetArcRelRepository assetArcRelRepository;
  private final IdentityAssetRelRepository identityAssetRelRepository;
  private final AiReferenceItemRepository aiReferenceItemRepository;
  private final AiReferenceSessionRepository aiReferenceSessionRepository;
  private final CharacterProfileRepository characterProfileRepository;
  private final CharacterIdentityRepository characterIdentityRepository;
  private final StorySeriesRepository storySeriesRepository;
  private final StoryArcRepository storyArcRepository;
  private final AssetComboRepository comboRepository;
  private final AssetComboMemberRepository comboMemberRepository;
  private final StoryPageRepository storyPageRepository;
  private final PageAssetRefRepository pageAssetRefRepository;
  private final PageComboRefRepository pageComboRefRepository;
  private final ObjectMapper objectMapper;

  public AssetAssociationLifecycle(
      AssetAssociationSnapshotRepository snapshotRepository,
      AssetCharacterRelRepository characterRelRepository,
      AssetSeriesRelRepository assetSeriesRelRepository,
      AssetArcRelRepository assetArcRelRepository,
      IdentityAssetRelRepository identityAssetRelRepository,
      AiReferenceItemRepository aiReferenceItemRepository,
      AiReferenceSessionRepository aiReferenceSessionRepository,
      CharacterProfileRepository characterProfileRepository,
      CharacterIdentityRepository characterIdentityRepository,
      StorySeriesRepository storySeriesRepository,
      StoryArcRepository storyArcRepository,
      AssetComboRepository comboRepository,
      AssetComboMemberRepository comboMemberRepository,
      StoryPageRepository storyPageRepository,
      PageAssetRefRepository pageAssetRefRepository,
      PageComboRefRepository pageComboRefRepository,
      ObjectMapper objectMapper) {
    this.snapshotRepository = snapshotRepository;
    this.characterRelRepository = characterRelRepository;
    this.assetSeriesRelRepository = assetSeriesRelRepository;
    this.assetArcRelRepository = assetArcRelRepository;
    this.identityAssetRelRepository = identityAssetRelRepository;
    this.aiReferenceItemRepository = aiReferenceItemRepository;
    this.aiReferenceSessionRepository = aiReferenceSessionRepository;
    this.characterProfileRepository = characterProfileRepository;
    this.characterIdentityRepository = characterIdentityRepository;
    this.storySeriesRepository = storySeriesRepository;
    this.storyArcRepository = storyArcRepository;
    this.comboRepository = comboRepository;
    this.comboMemberRepository = comboMemberRepository;
    this.storyPageRepository = storyPageRepository;
    this.pageAssetRefRepository = pageAssetRefRepository;
    this.pageComboRefRepository = pageComboRefRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public void detachAll(Long assetId) {
    List<AssetAssociationSnapshot> snaps = new ArrayList<>();
    detachCharacterRels(assetId, snaps);
    detachSeriesRels(assetId, snaps);
    detachArcRels(assetId, snaps);
    detachIdentityRels(assetId, snaps);
    detachAiRefs(assetId, snaps);
    detachSeriesCovers(assetId, snaps);
    detachArcCovers(assetId, snaps);
    detachComboAndPages(assetId, snaps);
    snapshotRepository.deleteByAssetId(assetId);
    snapshotRepository.saveAll(snaps);
  }

  @Transactional
  public void restoreAll(Long assetId) {
    List<AssetAssociationSnapshot> snaps = snapshotRepository.findByAssetIdOrderByIdAsc(assetId);
    for (AssetAssociationSnapshot snap : snaps) {
      Map<String, Object> payload = readPayload(snap.getPayloadJson());
      switch (snap.getKind()) {
        case CHARACTER_REL -> restoreCharacterRel(assetId, payload);
        case SERIES_REL -> restoreSeriesRel(assetId, payload);
        case ARC_REL -> restoreArcRel(assetId, payload);
        case IDENTITY_REL -> restoreIdentityRel(assetId, payload);
        case AI_REF -> restoreAiRef(assetId, payload);
        case SERIES_COVER -> restoreSeriesCover(assetId, payload);
        case ARC_COVER -> restoreArcCover(assetId, payload);
        case COMBO_MEMBER, PAGE_BEAT_COVER, PAGE_COMBO_MEMBER_REF -> {}
        default -> {}
      }
    }
    restoreComboAndPages(assetId, snaps);
  }

  @Transactional
  public void purgeAll(Long assetId) {
    characterRelRepository.deleteByAssetId(assetId);
    assetSeriesRelRepository.deleteByAssetId(assetId);
    assetArcRelRepository.deleteByAssetId(assetId);
    identityAssetRelRepository.deleteByAssetId(assetId);
    aiReferenceItemRepository.deleteByAssetId(assetId);
    for (StorySeries series : storySeriesRepository.findByCoverAssetId(assetId)) {
      series.setCoverAssetId(null);
      storySeriesRepository.save(series);
    }
    for (StoryArc arc : storyArcRepository.findByCoverAssetId(assetId)) {
      arc.setCoverAssetId(null);
      storyArcRepository.save(arc);
    }
    purgeComboAndPages(assetId);
    snapshotRepository.deleteByAssetId(assetId);
  }

  private void detachCharacterRels(Long assetId, List<AssetAssociationSnapshot> snaps) {
    for (Long characterId : characterRelRepository.findCharacterIdsByAssetId(assetId)) {
      snaps.add(snapshot(assetId, CHARACTER_REL, Map.of("characterId", characterId)));
    }
    characterRelRepository.deleteByAssetId(assetId);
  }

  private void detachSeriesRels(Long assetId, List<AssetAssociationSnapshot> snaps) {
    for (Long seriesId : assetSeriesRelRepository.findSeriesIdsByAssetId(assetId)) {
      snaps.add(snapshot(assetId, SERIES_REL, Map.of("seriesId", seriesId)));
    }
    assetSeriesRelRepository.deleteByAssetId(assetId);
  }

  private void detachArcRels(Long assetId, List<AssetAssociationSnapshot> snaps) {
    for (Long arcId : assetArcRelRepository.findArcIdsByAssetId(assetId)) {
      snaps.add(snapshot(assetId, ARC_REL, Map.of("arcId", arcId)));
    }
    assetArcRelRepository.deleteByAssetId(assetId);
  }

  private void detachIdentityRels(Long assetId, List<AssetAssociationSnapshot> snaps) {
    for (IdentityAssetRel rel : identityAssetRelRepository.findByAssetId(assetId)) {
      snaps.add(snapshot(assetId, IDENTITY_REL, Map.of("identityId", rel.getIdentityId())));
    }
    identityAssetRelRepository.deleteByAssetId(assetId);
  }

  private void detachAiRefs(Long assetId, List<AssetAssociationSnapshot> snaps) {
    for (AiReferenceItem item : aiReferenceItemRepository.findByAssetId(assetId)) {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("sessionId", item.getSessionId());
      payload.put("sortOrder", item.getSortOrder());
      payload.put("purpose", item.getPurpose());
      payload.put("note", item.getNote());
      payload.put("strength", item.getStrength());
      snaps.add(snapshot(assetId, AI_REF, payload));
    }
    aiReferenceItemRepository.deleteByAssetId(assetId);
  }

  private void detachSeriesCovers(Long assetId, List<AssetAssociationSnapshot> snaps) {
    for (StorySeries series : storySeriesRepository.findByCoverAssetId(assetId)) {
      snaps.add(snapshot(assetId, SERIES_COVER, Map.of("seriesId", series.getId())));
      series.setCoverAssetId(null);
      storySeriesRepository.save(series);
    }
  }

  private void detachArcCovers(Long assetId, List<AssetAssociationSnapshot> snaps) {
    for (StoryArc arc : storyArcRepository.findByCoverAssetId(assetId)) {
      snaps.add(snapshot(assetId, ARC_COVER, Map.of("arcId", arc.getId())));
      arc.setCoverAssetId(null);
      storyArcRepository.save(arc);
    }
  }

  private void restoreCharacterRel(Long assetId, Map<String, Object> payload) {
    Long characterId = longValue(payload.get("characterId"));
    if (characterId == null || !characterProfileRepository.existsById(characterId)) {
      return;
    }
    characterRelRepository.save(new AssetCharacterRel(assetId, characterId));
  }

  private void restoreSeriesRel(Long assetId, Map<String, Object> payload) {
    Long seriesId = longValue(payload.get("seriesId"));
    if (seriesId == null || !storySeriesRepository.existsById(seriesId)) {
      return;
    }
    assetSeriesRelRepository.save(new AssetSeriesRel(assetId, seriesId));
  }

  private void restoreArcRel(Long assetId, Map<String, Object> payload) {
    Long arcId = longValue(payload.get("arcId"));
    if (arcId == null || !storyArcRepository.existsById(arcId)) {
      return;
    }
    assetArcRelRepository.save(new AssetArcRel(assetId, arcId));
  }

  private void restoreIdentityRel(Long assetId, Map<String, Object> payload) {
    Long identityId = longValue(payload.get("identityId"));
    if (identityId == null || !characterIdentityRepository.existsById(identityId)) {
      return;
    }
    identityAssetRelRepository.save(new IdentityAssetRel(identityId, assetId));
  }

  private void restoreAiRef(Long assetId, Map<String, Object> payload) {
    Long sessionId = longValue(payload.get("sessionId"));
    if (sessionId == null || !aiReferenceSessionRepository.existsById(sessionId)) {
      return;
    }
    AiReferenceItem item = new AiReferenceItem();
    item.setSessionId(sessionId);
    item.setAssetId(assetId);
    item.setSortOrder(intValue(payload.get("sortOrder")));
    item.setPurpose(stringValue(payload.get("purpose")));
    item.setNote(stringValue(payload.get("note")));
    item.setStrength(decimalValue(payload.get("strength")));
    aiReferenceItemRepository.save(item);
  }

  private void restoreSeriesCover(Long assetId, Map<String, Object> payload) {
    Long seriesId = longValue(payload.get("seriesId"));
    if (seriesId == null) {
      return;
    }
    storySeriesRepository
        .findById(seriesId)
        .ifPresent(
            series -> {
              series.setCoverAssetId(assetId);
              storySeriesRepository.save(series);
            });
  }

  private void restoreArcCover(Long assetId, Map<String, Object> payload) {
    Long arcId = longValue(payload.get("arcId"));
    if (arcId == null) {
      return;
    }
    storyArcRepository
        .findById(arcId)
        .ifPresent(
            arc -> {
              arc.setCoverAssetId(assetId);
              storyArcRepository.save(arc);
            });
  }

  private void detachComboAndPages(Long assetId, List<AssetAssociationSnapshot> snaps) {
    Set<Long> affectedComboIds = detachComboMembers(assetId, snaps);
    detachPageComboMemberRefs(assetId, affectedComboIds, snaps);
    detachPageBeatCovers(assetId, snaps);
  }

  private Set<Long> detachComboMembers(Long assetId, List<AssetAssociationSnapshot> snaps) {
    LinkedHashSet<Long> affectedComboIds = new LinkedHashSet<>();
    for (AssetComboMember member : comboMemberRepository.findByAssetId(assetId)) {
      AssetCombo combo = comboRepository.findById(member.getComboId()).orElse(null);
      if (combo == null) {
        comboMemberRepository.delete(member);
        continue;
      }
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("comboId", combo.getId());
      payload.put("memberNo", member.getMemberNo());
      payload.put("sortOrder", member.getSortOrder());
      payload.put("playSequenceBefore", combo.getPlaySequence());
      snaps.add(snapshot(assetId, COMBO_MEMBER, payload));
      comboMemberRepository.delete(member);
      comboMemberRepository.flush();
      rewritePlaySequence(combo);
      affectedComboIds.add(combo.getId());
    }
    return affectedComboIds;
  }

  private void rewritePlaySequence(AssetCombo combo) {
    Set<Integer> remainingNos =
        comboMemberRepository.findByComboIdOrderBySortOrderAscMemberNoAsc(combo.getId()).stream()
            .map(AssetComboMember::getMemberNo)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    combo.setPlaySequence(filterPlaySequence(combo.getPlaySequence(), remainingNos));
    comboRepository.save(combo);
  }

  private void detachPageComboMemberRefs(
      Long assetId, Set<Long> affectedComboIds, List<AssetAssociationSnapshot> snaps) {
    Set<Long> handledPageIds = new HashSet<>();
    for (Long comboId : affectedComboIds) {
      for (PageComboRef pageCombo : pageComboRefRepository.findByComboId(comboId)) {
        detachOnePageComboMemberRef(assetId, comboId, pageCombo.getPageId(), snaps);
        handledPageIds.add(pageCombo.getPageId());
      }
    }
    for (PageAssetRef ref : pageAssetRefRepository.findByAssetId(assetId)) {
      if (!REF_KIND_BEAT_COMBO_MEMBER.equals(ref.getRefKind())) {
        continue;
      }
      if (!handledPageIds.add(ref.getPageId())) {
        deletePageAssetRef(ref.getPageId(), assetId, REF_KIND_BEAT_COMBO_MEMBER);
        continue;
      }
      Long comboId = resolveComboIdOnPage(ref.getPageId(), affectedComboIds);
      detachOnePageComboMemberRef(assetId, comboId, ref.getPageId(), snaps);
    }
  }

  private void detachOnePageComboMemberRef(
      Long assetId, Long comboId, Long pageId, List<AssetAssociationSnapshot> snaps) {
    StoryPage page = storyPageRepository.findById(pageId).orElse(null);
    Long coverAssetIdBefore = null;
    boolean coverChanged = false;
    if (page != null && comboId != null) {
      JsonNode root = readPageContent(page);
      coverAssetIdBefore = coverAssetIdForCombo(root, comboId);
      if (assetId.equals(coverAssetIdBefore)) {
        Long newCover = firstFrameAssetId(comboId);
        patchComboCoverAssetId(root, comboId, newCover);
        savePageContent(page, root);
        coverChanged = true;
        deletePageAssetRef(pageId, assetId, REF_KIND_BEAT_COVER);
        if (newCover != null) {
          savePageAssetRefIfAbsent(pageId, newCover, REF_KIND_BEAT_COVER);
        }
      }
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("pageId", pageId);
    if (comboId != null) {
      payload.put("comboId", comboId);
    }
    payload.put("refKind", REF_KIND_BEAT_COMBO_MEMBER);
    if (coverChanged) {
      payload.put("coverAssetIdBefore", coverAssetIdBefore);
    }
    snaps.add(snapshot(assetId, PAGE_COMBO_MEMBER_REF, payload));
    deletePageAssetRef(pageId, assetId, REF_KIND_BEAT_COMBO_MEMBER);
  }

  private void detachPageBeatCovers(Long assetId, List<AssetAssociationSnapshot> snaps) {
    for (PageAssetRef ref : List.copyOf(pageAssetRefRepository.findByAssetId(assetId))) {
      if (!REF_KIND_BEAT_COVER.equals(ref.getRefKind())) {
        continue;
      }
      StoryPage page = storyPageRepository.findById(ref.getPageId()).orElse(null);
      if (page == null) {
        deletePageAssetRef(ref.getPageId(), assetId, REF_KIND_BEAT_COVER);
        continue;
      }
      JsonNode root = readPageContent(page);
      boolean foundCover = false;
      if (root != null && root.isArray()) {
        for (int beatIndex = 0; beatIndex < root.size(); beatIndex++) {
          JsonNode item = root.get(beatIndex);
          if (item == null || !item.isObject() || !"BEAT".equals(item.path("type").asText(null))) {
            continue;
          }
          JsonNode children = item.get("children");
          if (children == null || !children.isArray()) {
            continue;
          }
          for (int childIndex = 0; childIndex < children.size(); childIndex++) {
            JsonNode child = children.get(childIndex);
            if (child == null || !child.isObject() || !"COVER".equals(child.path("type").asText(null))) {
              continue;
            }
            if (!assetId.equals(jsonLong(child.get("assetId")))) {
              continue;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("pageId", page.getId());
            payload.put("beatIndex", beatIndex);
            payload.put("childIndex", childIndex);
            snaps.add(snapshot(assetId, PAGE_BEAT_COVER, payload));
            ((ObjectNode) child).putNull("assetId");
            ((ObjectNode) item).putNull("coverAssetId");
            foundCover = true;
          }
        }
      }
      if (foundCover) {
        savePageContent(page, root);
      }
      deletePageAssetRef(page.getId(), assetId, REF_KIND_BEAT_COVER);
    }
  }

  private void restoreComboAndPages(Long assetId, List<AssetAssociationSnapshot> snaps) {
    for (AssetAssociationSnapshot snap : snaps) {
      Map<String, Object> payload = readPayload(snap.getPayloadJson());
      switch (snap.getKind()) {
        case COMBO_MEMBER -> restoreComboMember(assetId, payload);
        case PAGE_BEAT_COVER -> restorePageBeatCover(assetId, payload);
        case PAGE_COMBO_MEMBER_REF -> restorePageComboMemberRef(assetId, payload);
        default -> {}
      }
    }
  }

  private void restoreComboMember(Long assetId, Map<String, Object> payload) {
    Long comboId = longValue(payload.get("comboId"));
    if (comboId == null || !comboRepository.existsById(comboId)) {
      return;
    }
    int memberNo = intValue(payload.get("memberNo"));
    int sortOrder = intValue(payload.get("sortOrder"));
    boolean slotTaken =
        comboMemberRepository.findByComboIdOrderBySortOrderAscMemberNoAsc(comboId).stream()
            .anyMatch(member -> member.getMemberNo() == memberNo);
    if (!slotTaken) {
      AssetComboMember member = new AssetComboMember();
      member.setComboId(comboId);
      member.setAssetId(assetId);
      member.setMemberNo(memberNo);
      member.setSortOrder(sortOrder);
      comboMemberRepository.save(member);
      comboMemberRepository.flush();
    }
    Set<Integer> existingNos =
        comboMemberRepository.findByComboIdOrderBySortOrderAscMemberNoAsc(comboId).stream()
            .map(AssetComboMember::getMemberNo)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    comboRepository
        .findById(comboId)
        .ifPresent(
            combo -> {
              combo.setPlaySequence(
                  filterPlaySequence(stringValue(payload.get("playSequenceBefore")), existingNos));
              comboRepository.save(combo);
            });
  }

  private void restorePageBeatCover(Long assetId, Map<String, Object> payload) {
    Long pageId = longValue(payload.get("pageId"));
    if (pageId == null) {
      return;
    }
    StoryPage page = storyPageRepository.findById(pageId).orElse(null);
    if (page == null) {
      return;
    }
    int beatIndex = intValue(payload.get("beatIndex"));
    int childIndex = intValue(payload.get("childIndex"));
    JsonNode root = readPageContent(page);
    if (root == null || !root.isArray() || beatIndex < 0 || beatIndex >= root.size()) {
      return;
    }
    JsonNode beat = root.get(beatIndex);
    if (beat == null || !beat.isObject() || !"BEAT".equals(beat.path("type").asText(null))) {
      return;
    }
    JsonNode children = beat.get("children");
    if (children == null
        || !children.isArray()
        || childIndex < 0
        || childIndex >= children.size()) {
      return;
    }
    JsonNode child = children.get(childIndex);
    if (child == null || !child.isObject() || !"COVER".equals(child.path("type").asText(null))) {
      return;
    }
    ((ObjectNode) child).put("assetId", assetId);
    ((ObjectNode) beat).put("coverAssetId", assetId);
    savePageContent(page, root);
    savePageAssetRefIfAbsent(pageId, assetId, REF_KIND_BEAT_COVER);
  }

  private void restorePageComboMemberRef(Long assetId, Map<String, Object> payload) {
    Long pageId = longValue(payload.get("pageId"));
    Long comboId = longValue(payload.get("comboId"));
    if (pageId == null || comboId == null) {
      return;
    }
    StoryPage page = storyPageRepository.findById(pageId).orElse(null);
    if (page == null || !comboRepository.existsById(comboId)) {
      return;
    }
    JsonNode root = readPageContent(page);
    if (!pageHasCombo(root, comboId)) {
      return;
    }
    List<AssetComboMember> members =
        comboMemberRepository.findByComboIdOrderBySortOrderAscMemberNoAsc(comboId);
    for (AssetComboMember member : members) {
      savePageAssetRefIfAbsent(pageId, member.getAssetId(), REF_KIND_BEAT_COMBO_MEMBER);
    }
    if (payload.get("coverAssetIdBefore") == null) {
      return;
    }
    Long firstFrame = firstFrameAssetId(comboId);
    if (!assetId.equals(firstFrame)) {
      return;
    }
    patchComboCoverAssetId(root, comboId, assetId);
    savePageContent(page, root);
    savePageAssetRefIfAbsent(pageId, assetId, REF_KIND_BEAT_COVER);
  }

  private Long resolveComboIdOnPage(Long pageId, Set<Long> preferredComboIds) {
    StoryPage page = storyPageRepository.findById(pageId).orElse(null);
    if (page == null) {
      return preferredComboIds.isEmpty() ? null : preferredComboIds.iterator().next();
    }
    JsonNode root = readPageContent(page);
    Long fallback = null;
    if (root != null && root.isArray()) {
      for (JsonNode item : root) {
        if (item == null || !item.isObject() || !"BEAT".equals(item.path("type").asText(null))) {
          continue;
        }
        JsonNode children = item.get("children");
        if (children == null || !children.isArray()) {
          continue;
        }
        for (JsonNode child : children) {
          if (child == null || !child.isObject() || !"COMBO".equals(child.path("type").asText(null))) {
            continue;
          }
          Long comboId = jsonLong(child.get("comboId"));
          if (comboId == null) {
            continue;
          }
          if (preferredComboIds.contains(comboId)) {
            return comboId;
          }
          if (fallback == null) {
            fallback = comboId;
          }
        }
      }
    }
    return fallback;
  }

  private boolean pageHasCombo(JsonNode root, Long comboId) {
    if (root == null || !root.isArray() || comboId == null) {
      return false;
    }
    for (JsonNode item : root) {
      if (item == null || !item.isObject() || !"BEAT".equals(item.path("type").asText(null))) {
        continue;
      }
      JsonNode children = item.get("children");
      if (children == null || !children.isArray()) {
        continue;
      }
      for (JsonNode child : children) {
        if (child != null
            && child.isObject()
            && "COMBO".equals(child.path("type").asText(null))
            && comboId.equals(jsonLong(child.get("comboId")))) {
          return true;
        }
      }
    }
    return false;
  }

  private Long coverAssetIdForCombo(JsonNode root, Long comboId) {
    if (root == null || !root.isArray() || comboId == null) {
      return null;
    }
    for (JsonNode item : root) {
      if (item == null || !item.isObject() || !"BEAT".equals(item.path("type").asText(null))) {
        continue;
      }
      JsonNode children = item.get("children");
      if (children == null || !children.isArray()) {
        continue;
      }
      for (JsonNode child : children) {
        if (child != null
            && child.isObject()
            && "COMBO".equals(child.path("type").asText(null))
            && comboId.equals(jsonLong(child.get("comboId")))) {
          return jsonLong(item.get("coverAssetId"));
        }
      }
    }
    return null;
  }

  private void patchComboCoverAssetId(JsonNode root, Long comboId, Long coverAssetId) {
    if (root == null || !root.isArray() || comboId == null) {
      return;
    }
    for (JsonNode item : root) {
      if (item == null || !item.isObject() || !"BEAT".equals(item.path("type").asText(null))) {
        continue;
      }
      JsonNode children = item.get("children");
      if (children == null || !children.isArray()) {
        continue;
      }
      for (JsonNode child : children) {
        if (child == null
            || !child.isObject()
            || !"COMBO".equals(child.path("type").asText(null))
            || !comboId.equals(jsonLong(child.get("comboId")))) {
          continue;
        }
        if (coverAssetId == null) {
          ((ObjectNode) item).putNull("coverAssetId");
        } else {
          ((ObjectNode) item).put("coverAssetId", coverAssetId);
        }
      }
    }
  }

  private Long firstFrameAssetId(Long comboId) {
    List<AssetComboMember> members =
        comboMemberRepository.findByComboIdOrderBySortOrderAscMemberNoAsc(comboId);
    if (members.isEmpty()) {
      return null;
    }
    Map<Integer, AssetComboMember> byNo =
        members.stream()
            .collect(
                Collectors.toMap(
                    AssetComboMember::getMemberNo, Function.identity(), (a, b) -> a));
    AssetCombo combo = comboRepository.findById(comboId).orElse(null);
    List<Integer> steps =
        parseExistingPlaySequence(combo == null ? null : combo.getPlaySequence(), byNo.keySet());
    if (steps.isEmpty()) {
      return members.get(0).getAssetId();
    }
    AssetComboMember first = byNo.get(steps.get(0));
    return first == null ? members.get(0).getAssetId() : first.getAssetId();
  }

  private static List<Integer> parseExistingPlaySequence(String playSequence, Set<Integer> memberNos) {
    List<Integer> steps = new ArrayList<>();
    if (playSequence == null || playSequence.isBlank() || memberNos == null || memberNos.isEmpty()) {
      return steps;
    }
    for (String part : playSequence.split(",")) {
      String token = part.trim();
      if (token.isEmpty()) {
        continue;
      }
      try {
        int memberNo = Integer.parseInt(token);
        if (memberNos.contains(memberNo)) {
          steps.add(memberNo);
        }
      } catch (NumberFormatException ignored) {
        // skip illegal tokens when rewriting after detach
      }
    }
    return steps;
  }

  private static String filterPlaySequence(String playSequence, Set<Integer> remainingNos) {
    List<Integer> steps = parseExistingPlaySequence(playSequence, remainingNos);
    if (steps.isEmpty()) {
      return "";
    }
    return steps.stream().map(String::valueOf).collect(Collectors.joining(","));
  }

  private JsonNode readPageContent(StoryPage page) {
    try {
      return objectMapper.readTree(page.getContentJson());
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("invalid page content_json", ex);
    }
  }

  private void savePageContent(StoryPage page, JsonNode root) {
    try {
      page.setContentJson(objectMapper.writeValueAsString(root));
      storyPageRepository.save(page);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("failed to write page content_json", ex);
    }
  }

  private static Long jsonLong(JsonNode node) {
    if (node == null || node.isNull() || !node.isIntegralNumber()) {
      return null;
    }
    return node.asLong();
  }

  private void deletePageAssetRef(Long pageId, Long assetId, String refKind) {
    PageAssetRefId id = new PageAssetRefId(pageId, assetId, refKind);
    if (pageAssetRefRepository.existsById(id)) {
      pageAssetRefRepository.deleteById(id);
    }
  }

  private void savePageAssetRefIfAbsent(Long pageId, Long assetId, String refKind) {
    if (pageId == null || assetId == null || refKind == null) {
      return;
    }
    PageAssetRefId id = new PageAssetRefId(pageId, assetId, refKind);
    if (!pageAssetRefRepository.existsById(id)) {
      pageAssetRefRepository.save(new PageAssetRef(pageId, assetId, refKind));
    }
  }

  private void purgeComboAndPages(Long assetId) {
    detachComboAndPages(assetId, new ArrayList<>());
  }

  private AssetAssociationSnapshot snapshot(Long assetId, String kind, Map<String, ?> payload) {
    AssetAssociationSnapshot snap = new AssetAssociationSnapshot();
    snap.setAssetId(assetId);
    snap.setKind(kind);
    snap.setPayloadJson(writeJson(payload));
    snap.setCreatedAt(LocalDateTime.now());
    return snap;
  }

  private String writeJson(Map<String, ?> payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("failed to serialize association snapshot", ex);
    }
  }

  private Map<String, Object> readPayload(String json) {
    try {
      return objectMapper.readValue(json, PAYLOAD_TYPE);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("invalid association snapshot payload", ex);
    }
  }

  private static Long longValue(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number number) {
      return number.longValue();
    }
    String text = String.valueOf(value).trim();
    if (text.isEmpty()) {
      return null;
    }
    return Long.parseLong(text);
  }

  private static int intValue(Object value) {
    if (value == null) {
      return 0;
    }
    if (value instanceof Number number) {
      return number.intValue();
    }
    return Integer.parseInt(String.valueOf(value).trim());
  }

  private static String stringValue(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private static BigDecimal decimalValue(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof BigDecimal decimal) {
      return decimal;
    }
    if (value instanceof Integer || value instanceof Long) {
      return BigDecimal.valueOf(((Number) value).longValue());
    }
    return new BigDecimal(String.valueOf(value));
  }
}
