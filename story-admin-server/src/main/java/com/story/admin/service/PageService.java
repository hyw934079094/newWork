package com.story.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.story.admin.domain.Asset;
import com.story.admin.domain.AssetStatus;
import com.story.admin.domain.PageAssetRef;
import com.story.admin.domain.StoryPage;
import com.story.admin.dto.PageCreateRequest;
import com.story.admin.dto.PageUpdateRequest;
import com.story.admin.repository.AssetRepository;
import com.story.admin.repository.PageAssetRefRepository;
import com.story.admin.repository.StoryPageRepository;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PageService {

  static final String DEFAULT_CONTENT = "[]";
  static final String REF_KIND_BEAT_COVER = "BEAT_COVER";
  private static final Set<String> TOP_LEVEL_TYPES = Set.of("TITLE", "BODY", "DIVIDER", "BEAT");
  private static final Set<String> BEAT_CHILD_TYPES = Set.of("BODY", "DIALOGUE");

  private final StoryPageRepository pageRepository;
  private final PageAssetRefRepository pageAssetRefRepository;
  private final AssetRepository assetRepository;
  private final ArcService arcService;
  private final ObjectMapper objectMapper;

  public PageService(
      StoryPageRepository pageRepository,
      PageAssetRefRepository pageAssetRefRepository,
      AssetRepository assetRepository,
      ArcService arcService,
      ObjectMapper objectMapper) {
    this.pageRepository = pageRepository;
    this.pageAssetRefRepository = pageAssetRefRepository;
    this.assetRepository = assetRepository;
    this.arcService = arcService;
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
      int next =
          pageRepository.findMaxSortOrderByArcId(arcId).orElse(-1) + 1;
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
    Set<Long> coverIds = validateAndCollectBeatCovers(contentJson);
    page.setContentJson(contentJson);
    StoryPage saved = pageRepository.save(page);
    rebuildRefs(saved.getId(), coverIds);
    return saved;
  }

  @Transactional
  public void delete(Long id) {
    if (!pageRepository.existsById(id)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "page not found: " + id);
    }
    pageAssetRefRepository.deleteByPageId(id);
    pageAssetRefRepository.flush();
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
    if (orderedIds == null || orderedIds.size() != existing.size() || !existing.containsAll(orderedIds)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page reorder ids mismatch");
    }
    for (int i = 0; i < orderedIds.size(); i++) {
      Long pageId = orderedIds.get(i);
      StoryPage page = get(pageId);
      page.setSortOrder(i);
      pageRepository.save(page);
    }
  }

  private void rebuildRefs(Long pageId, Set<Long> coverIds) {
    pageAssetRefRepository.deleteByPageId(pageId);
    pageAssetRefRepository.flush();
    for (Long assetId : coverIds) {
      pageAssetRefRepository.save(new PageAssetRef(pageId, assetId, REF_KIND_BEAT_COVER));
    }
  }

  private String normalizeContent(String contentJson) {
    if (contentJson == null || contentJson.isBlank()) {
      return DEFAULT_CONTENT;
    }
    return contentJson;
  }

  private Set<Long> validateAndCollectBeatCovers(String contentJson) {
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
    for (JsonNode item : root) {
      if (item == null || !item.isObject()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content_json item must be an object");
      }
      String type = item.path("type").asText(null);
      if (type == null || !TOP_LEVEL_TYPES.contains(type)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "illegal content type: " + type);
      }
      if ("BEAT".equals(type)) {
        JsonNode coverNode = item.get("coverAssetId");
        if (coverNode == null || coverNode.isNull() || !coverNode.isIntegralNumber()) {
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST, "BEAT coverAssetId is required");
        }
        long coverAssetId = coverNode.asLong();
        validateNormalAsset(coverAssetId);
        coverIds.add(coverAssetId);
        JsonNode children = item.get("children");
        if (children != null && !children.isNull()) {
          if (!children.isArray()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "BEAT children must be an array");
          }
          for (JsonNode child : children) {
            if (child == null || !child.isObject()) {
              throw new ResponseStatusException(
                  HttpStatus.BAD_REQUEST, "BEAT child must be an object");
            }
            String childType = child.path("type").asText(null);
            if (childType == null || !BEAT_CHILD_TYPES.contains(childType)) {
              throw new ResponseStatusException(
                  HttpStatus.BAD_REQUEST, "illegal BEAT child type: " + childType);
            }
          }
        }
      }
    }
    return coverIds;
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
}
