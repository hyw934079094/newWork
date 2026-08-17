package com.story.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.story.admin.domain.Asset;
import com.story.admin.domain.AssetStatus;
import com.story.admin.domain.PageAssetRef;
import com.story.admin.domain.PageComboRef;
import com.story.admin.domain.StoryPage;
import com.story.admin.dto.ComboDetailResponse;
import com.story.admin.dto.PageCreateRequest;
import com.story.admin.dto.PageUpdateRequest;
import com.story.admin.repository.AssetRepository;
import com.story.admin.repository.PageAssetRefRepository;
import com.story.admin.repository.PageComboRefRepository;
import com.story.admin.repository.StoryPageRepository;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PageService {

  static final String DEFAULT_CONTENT = "[]";
  static final String REF_KIND_BEAT_COVER = "BEAT_COVER";
  static final String REF_KIND_BEAT_COMBO_MEMBER = "BEAT_COMBO_MEMBER";
  private static final Set<String> TOP_LEVEL_TYPES = Set.of("TITLE", "BODY", "DIVIDER", "BEAT");
  private static final Set<String> BEAT_CHILD_TYPES = Set.of("COVER", "COMBO", "BODY", "DIALOGUE");

  private final StoryPageRepository pageRepository;
  private final PageAssetRefRepository pageAssetRefRepository;
  private final PageComboRefRepository pageComboRefRepository;
  private final AssetRepository assetRepository;
  private final ArcService arcService;
  private final ComboService comboService;
  private final ObjectMapper objectMapper;

  public PageService(
      StoryPageRepository pageRepository,
      PageAssetRefRepository pageAssetRefRepository,
      PageComboRefRepository pageComboRefRepository,
      AssetRepository assetRepository,
      ArcService arcService,
      ComboService comboService,
      ObjectMapper objectMapper) {
    this.pageRepository = pageRepository;
    this.pageAssetRefRepository = pageAssetRefRepository;
    this.pageComboRefRepository = pageComboRefRepository;
    this.assetRepository = assetRepository;
    this.arcService = arcService;
    this.comboService = comboService;
    this.objectMapper = objectMapper;
  }

  public List<StoryPage> listByArc(Long arcId) {
    arcService.get(arcId);
    return pageRepository.findByArcIdOrderBySortOrderAscIdAsc(arcId);
  }

  public StoryPage get(Long id) {
    return pageRepository
        .findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "page not found: " + id));
  }

  @Transactional
  public StoryPage create(Long arcId, PageCreateRequest req) {
    arcService.get(arcId);
    if (req == null || req.title() == null || req.title().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page title is required");
    }
    StoryPage page = new StoryPage();
    page.setArcId(arcId);
    page.setTitle(req.title().trim());
    page.setContentJson(DEFAULT_CONTENT);
    if (req.sortOrder() != null) {
      page.setSortOrder(req.sortOrder());
    } else {
      int next = pageRepository.findMaxSortOrderByArcId(arcId).orElse(-1) + 1;
      page.setSortOrder(next);
    }
    return pageRepository.save(page);
  }

  @Transactional
  public StoryPage update(Long id, PageUpdateRequest req) {
    if (req == null || req.title() == null || req.title().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page title is required");
    }
    StoryPage page = get(id);
    page.setTitle(req.title().trim());
    if (req.sortOrder() != null) {
      page.setSortOrder(req.sortOrder());
    }
    String contentJson = normalizeContent(req.contentJson());
    BeatRefBundle refs = validateAndCollectRefs(contentJson);
    page.setContentJson(contentJson);
    StoryPage saved = pageRepository.save(page);
    rebuildRefs(saved.getId(), refs);
    return saved;
  }

  @Transactional
  public void delete(Long id) {
    if (!pageRepository.existsById(id)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "page not found: " + id);
    }
    pageAssetRefRepository.deleteByPageId(id);
    pageComboRefRepository.deleteByPageId(id);
    pageAssetRefRepository.flush();
    pageComboRefRepository.flush();
    pageRepository.deleteById(id);
  }

  @Transactional
  public void reorder(Long arcId, List<Long> orderedIds) {
    arcService.get(arcId);
    List<StoryPage> pages = pageRepository.findByArcId(arcId);
    Set<Long> existing = new HashSet<>();
    for (StoryPage page : pages) {
      existing.add(page.getId());
    }
    if (orderedIds == null
        || orderedIds.size() != existing.size()
        || !existing.containsAll(orderedIds)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page reorder ids mismatch");
    }
    for (int i = 0; i < orderedIds.size(); i++) {
      Long pageId = orderedIds.get(i);
      StoryPage page = get(pageId);
      page.setSortOrder(i);
      pageRepository.save(page);
    }
  }

  private void rebuildRefs(Long pageId, BeatRefBundle refs) {
    pageAssetRefRepository.deleteByPageId(pageId);
    pageComboRefRepository.deleteByPageId(pageId);
    pageAssetRefRepository.flush();
    pageComboRefRepository.flush();
    for (Long assetId : refs.coverIds()) {
      pageAssetRefRepository.save(new PageAssetRef(pageId, assetId, REF_KIND_BEAT_COVER));
    }
    for (Long assetId : refs.comboMemberIds()) {
      pageAssetRefRepository.save(new PageAssetRef(pageId, assetId, REF_KIND_BEAT_COMBO_MEMBER));
    }
    for (Long comboId : refs.comboIds()) {
      pageComboRefRepository.save(new PageComboRef(pageId, comboId));
    }
  }

  private String normalizeContent(String contentJson) {
    if (contentJson == null || contentJson.isBlank()) {
      return DEFAULT_CONTENT;
    }
    JsonNode root;
    try {
      root = objectMapper.readTree(contentJson);
    } catch (JsonProcessingException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content_json is not valid JSON");
    }
    if (root == null || !root.isArray()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content_json must be an array");
    }
    for (JsonNode item : root) {
      if (item != null && item.isObject() && "BEAT".equals(item.path("type").asText(null))) {
        normalizeBeat((ObjectNode) item);
      }
    }
    try {
      return objectMapper.writeValueAsString(root);
    } catch (JsonProcessingException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content_json normalize failed");
    }
  }

  private void normalizeBeat(ObjectNode beat) {
    ArrayNode children;
    JsonNode childrenNode = beat.get("children");
    if (childrenNode == null || childrenNode.isNull()) {
      children = objectMapper.createArrayNode();
      beat.set("children", children);
    } else if (!childrenNode.isArray()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BEAT children must be an array");
    } else {
      children = (ArrayNode) childrenNode;
    }

    int coverIndex = -1;
    int comboIndex = -1;
    Long coverFromChild = null;
    Long comboId = null;
    for (int i = 0; i < children.size(); i++) {
      JsonNode child = children.get(i);
      if (child == null || !child.isObject()) {
        continue;
      }
      String childType = child.path("type").asText(null);
      if ("COVER".equals(childType)) {
        if (coverIndex >= 0) {
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST, "BEAT must contain at most one COVER child");
        }
        coverIndex = i;
        JsonNode assetIdNode = child.get("assetId");
        if (assetIdNode != null && !assetIdNode.isNull() && assetIdNode.isIntegralNumber()) {
          coverFromChild = assetIdNode.asLong();
        }
      } else if ("COMBO".equals(childType)) {
        if (comboIndex >= 0) {
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST, "BEAT must contain at most one COMBO child");
        }
        comboIndex = i;
        JsonNode comboIdNode = child.get("comboId");
        if (comboIdNode != null && !comboIdNode.isNull() && comboIdNode.isIntegralNumber()) {
          comboId = comboIdNode.asLong();
        }
      }
    }

    if (coverIndex >= 0 && comboIndex >= 0) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "BEAT cannot contain both COVER and COMBO");
    }

    JsonNode legacyCover = beat.get("coverAssetId");
    Long legacyId = null;
    if (legacyCover != null && !legacyCover.isNull() && legacyCover.isIntegralNumber()) {
      legacyId = legacyCover.asLong();
    }

    if (comboIndex >= 0) {
      if (comboId == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BEAT COMBO comboId is required");
      }
      ComboDetailResponse combo = comboService.get(comboId);
      Long firstFrame = firstFrameAssetId(combo);
      beat.put("coverAssetId", firstFrame);
      return;
    }

    if (coverIndex < 0) {
      if (legacyId == null) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "BEAT COVER or COMBO is required");
      }
      ObjectNode cover = objectMapper.createObjectNode();
      cover.put("type", "COVER");
      cover.put("assetId", legacyId);
      children.insert(0, cover);
      coverFromChild = legacyId;
    } else if (coverFromChild == null) {
      if (legacyId == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BEAT COVER assetId is required");
      }
      ((ObjectNode) children.get(coverIndex)).put("assetId", legacyId);
      coverFromChild = legacyId;
    }

    beat.put("coverAssetId", coverFromChild);
  }

  private BeatRefBundle validateAndCollectRefs(String contentJson) {
    JsonNode root;
    try {
      root = objectMapper.readTree(contentJson);
    } catch (JsonProcessingException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content_json is not valid JSON");
    }
    if (root == null || !root.isArray()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content_json must be an array");
    }
    LinkedHashSet<Long> coverIds = new LinkedHashSet<>();
    LinkedHashSet<Long> comboMemberIds = new LinkedHashSet<>();
    LinkedHashSet<Long> comboIds = new LinkedHashSet<>();
    for (JsonNode item : root) {
      if (item == null || !item.isObject()) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "content_json item must be an object");
      }
      String type = item.path("type").asText(null);
      if (type == null || !TOP_LEVEL_TYPES.contains(type)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "illegal content type: " + type);
      }
      if (!"BEAT".equals(type)) {
        continue;
      }
      JsonNode coverNode = item.get("coverAssetId");
      if (coverNode == null || coverNode.isNull() || !coverNode.isIntegralNumber()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BEAT coverAssetId is required");
      }
      long coverAssetId = coverNode.asLong();
      validateNormalAsset(coverAssetId);
      coverIds.add(coverAssetId);

      JsonNode children = item.get("children");
      if (children == null || children.isNull() || !children.isArray()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BEAT children must be an array");
      }
      int coverCount = 0;
      int comboCount = 0;
      for (JsonNode child : children) {
        if (child == null || !child.isObject()) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BEAT child must be an object");
        }
        String childType = child.path("type").asText(null);
        if (childType == null || !BEAT_CHILD_TYPES.contains(childType)) {
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST, "illegal BEAT child type: " + childType);
        }
        if ("COVER".equals(childType)) {
          coverCount++;
          JsonNode assetIdNode = child.get("assetId");
          if (assetIdNode == null || assetIdNode.isNull() || !assetIdNode.isIntegralNumber()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "BEAT COVER assetId is required");
          }
          long childAssetId = assetIdNode.asLong();
          if (childAssetId != coverAssetId) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "BEAT coverAssetId must match COVER assetId");
          }
          validateNormalAsset(childAssetId);
        } else if ("COMBO".equals(childType)) {
          comboCount++;
          JsonNode comboIdNode = child.get("comboId");
          if (comboIdNode == null || comboIdNode.isNull() || !comboIdNode.isIntegralNumber()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "BEAT COMBO comboId is required");
          }
          long comboId = comboIdNode.asLong();
          ComboDetailResponse combo = comboService.get(comboId);
          if (combo.members() == null || combo.members().isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "combo has no members: " + comboId);
          }
          for (ComboDetailResponse.MemberView member : combo.members()) {
            validateNormalAsset(member.assetId());
            comboMemberIds.add(member.assetId());
          }
          Long firstFrame = firstFrameAssetId(combo);
          if (!firstFrame.equals(coverAssetId)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "BEAT coverAssetId must match COMBO first frame");
          }
          comboIds.add(comboId);
        }
      }
      if (coverCount + comboCount != 1) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "BEAT must contain exactly one COVER or COMBO child");
      }
    }
    return new BeatRefBundle(coverIds, comboMemberIds, comboIds);
  }

  static Long firstFrameAssetId(ComboDetailResponse combo) {
    Map<Integer, ComboDetailResponse.MemberView> byNo =
        combo.members().stream()
            .collect(Collectors.toMap(ComboDetailResponse.MemberView::memberNo, Function.identity()));
    List<Integer> steps = parsePlaySequence(combo.playSequence(), byNo.keySet());
    if (steps.isEmpty()) {
      ComboDetailResponse.MemberView first = combo.members().get(0);
      return first.assetId();
    }
    ComboDetailResponse.MemberView member = byNo.get(steps.get(0));
    if (member == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "combo playSequence first step invalid");
    }
    return member.assetId();
  }

  static List<Integer> parsePlaySequence(String playSequence, Set<Integer> memberNos) {
    if (playSequence == null || playSequence.isBlank()) {
      return memberNos.stream().sorted().toList();
    }
    String[] parts = playSequence.split(",");
    java.util.ArrayList<Integer> steps = new java.util.ArrayList<>();
    for (String part : parts) {
      String token = part.trim();
      if (token.isEmpty()) {
        continue;
      }
      int n;
      try {
        n = Integer.parseInt(token);
      } catch (NumberFormatException ex) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "playSequence items must be positive integers");
      }
      if (n < 1 || !memberNos.contains(n)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "playSequence contains unknown memberNo: " + n);
      }
      steps.add(n);
    }
    return steps;
  }

  private void validateNormalAsset(Long assetId) {
    Asset asset =
        assetRepository
            .findById(assetId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "cover asset not found: " + assetId));
    if (asset.getStatus() != AssetStatus.NORMAL) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "cover asset is not available: " + assetId);
    }
  }

  private record BeatRefBundle(
      Set<Long> coverIds, Set<Long> comboMemberIds, Set<Long> comboIds) {}
}
