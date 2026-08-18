package com.story.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.story.admin.domain.AiReferenceItem;
import com.story.admin.domain.AssetArcRel;
import com.story.admin.domain.AssetAssociationSnapshot;
import com.story.admin.domain.AssetCharacterRel;
import com.story.admin.domain.AssetSeriesRel;
import com.story.admin.domain.IdentityAssetRel;
import com.story.admin.domain.StoryArc;
import com.story.admin.domain.StorySeries;
import com.story.admin.repository.AiReferenceItemRepository;
import com.story.admin.repository.AiReferenceSessionRepository;
import com.story.admin.repository.AssetArcRelRepository;
import com.story.admin.repository.AssetAssociationSnapshotRepository;
import com.story.admin.repository.AssetCharacterRelRepository;
import com.story.admin.repository.AssetSeriesRelRepository;
import com.story.admin.repository.CharacterIdentityRepository;
import com.story.admin.repository.CharacterProfileRepository;
import com.story.admin.repository.IdentityAssetRelRepository;
import com.story.admin.repository.StoryArcRepository;
import com.story.admin.repository.StorySeriesRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

  private void detachComboAndPages(Long assetId, List<AssetAssociationSnapshot> snaps) {}

  private void restoreComboAndPages(Long assetId, List<AssetAssociationSnapshot> snaps) {}

  private void purgeComboAndPages(Long assetId) {}

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
