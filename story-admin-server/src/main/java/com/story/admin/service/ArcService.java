package com.story.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.story.admin.domain.ArcStatus;
import com.story.admin.domain.Asset;
import com.story.admin.domain.AssetStatus;
import com.story.admin.domain.StoryArc;
import com.story.admin.domain.StoryPage;
import com.story.admin.dto.ArcCreateRequest;
import com.story.admin.dto.ArcQuery;
import com.story.admin.dto.ArcReadingStreamResponse;
import com.story.admin.dto.ArcUpdateRequest;
import com.story.admin.repository.AssetRepository;
import com.story.admin.repository.PageAssetRefRepository;
import com.story.admin.repository.StoryArcRepository;
import com.story.admin.repository.StoryPageRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ArcService {

  private static final Logger log = LoggerFactory.getLogger(ArcService.class);

  private final StoryArcRepository repo;
  private final SeriesService seriesService;
  private final AssetRepository assetRepository;
  private final StoryPageRepository pageRepository;
  private final PageAssetRefRepository pageAssetRefRepository;
  private final ObjectMapper objectMapper;

  public ArcService(
      StoryArcRepository repo,
      SeriesService seriesService,
      AssetRepository assetRepository,
      StoryPageRepository pageRepository,
      PageAssetRefRepository pageAssetRefRepository,
      ObjectMapper objectMapper) {
    this.repo = repo;
    this.seriesService = seriesService;
    this.assetRepository = assetRepository;
    this.pageRepository = pageRepository;
    this.pageAssetRefRepository = pageAssetRefRepository;
    this.objectMapper = objectMapper;
  }

  public List<StoryArc> listBySeries(Long seriesId, ArcQuery query) {
    seriesService.get(seriesId);
    ArcQuery q = query == null ? new ArcQuery(null) : query;
    return repo.findAll(
        buildSpec(seriesId, q), Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("id")));
  }

  public StoryArc get(Long id) {
    return repo.findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "arc not found: " + id));
  }

  @Transactional
  public StoryArc create(Long seriesId, ArcCreateRequest req) {
    seriesService.get(seriesId);
    if (req == null || req.title() == null || req.title().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "arc title is required");
    }
    StoryArc arc = new StoryArc();
    arc.setSeriesId(seriesId);
    arc.setCode(nextCode());
    arc.setSortOrder(0);
    applyFields(arc, req.title(), req.status(), req.summary(), req.coverAssetId(), true);
    return repo.save(arc);
  }

  @Transactional
  public StoryArc update(Long id, ArcUpdateRequest req) {
    if (req == null || req.title() == null || req.title().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "arc title is required");
    }
    StoryArc arc = get(id);
    applyFields(arc, req.title(), req.status(), req.summary(), req.coverAssetId(), false);
    return repo.save(arc);
  }

  @Transactional
  public void delete(Long id) {
    if (!repo.existsById(id)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "arc not found: " + id);
    }
    List<StoryPage> pages = pageRepository.findByArcId(id);
    for (StoryPage page : pages) {
      pageAssetRefRepository.deleteByPageId(page.getId());
    }
    pageAssetRefRepository.flush();
    pageRepository.deleteAll(pages);
    pageRepository.flush();
    repo.deleteById(id);
  }

  public ArcReadingStreamResponse readingStream(Long arcId) {
    StoryArc arc = get(arcId);
    List<StoryPage> pages = pageRepository.findByArcIdOrderBySortOrderAscIdAsc(arcId);
    List<Map<String, Object>> segments = new ArrayList<>();
    if (arc.getCoverAssetId() != null) {
      Map<String, Object> cover = new LinkedHashMap<>();
      cover.put("type", "ARC_COVER");
      cover.put("assetId", arc.getCoverAssetId());
      cover.put("contentPath", contentPath(arc.getCoverAssetId()));
      segments.add(cover);
    }
    Map<String, Object> arcTitle = new LinkedHashMap<>();
    arcTitle.put("type", "ARC_TITLE");
    arcTitle.put("text", arc.getTitle());
    segments.add(arcTitle);
    if (arc.getSummary() != null && !arc.getSummary().isBlank()) {
      Map<String, Object> summary = new LinkedHashMap<>();
      summary.put("type", "ARC_SUMMARY");
      summary.put("text", arc.getSummary());
      segments.add(summary);
    }
    for (StoryPage page : pages) {
      Map<String, Object> pageTitle = new LinkedHashMap<>();
      pageTitle.put("type", "PAGE_TITLE");
      pageTitle.put("pageId", page.getId());
      pageTitle.put("pageSortOrder", page.getSortOrder());
      pageTitle.put("text", page.getTitle());
      segments.add(pageTitle);
      appendContentSegments(segments, page);
    }
    return new ArcReadingStreamResponse(
        arc.getId(),
        arc.getTitle(),
        arc.getSummary(),
        arc.getCoverAssetId(),
        contentPath(arc.getCoverAssetId()),
        pages.size(),
        segments);
  }

  private void appendContentSegments(List<Map<String, Object>> segments, StoryPage page) {
    JsonNode root = parseContentArray(page.getContentJson());
    if (root == null) {
      return;
    }
    Long pageId = page.getId();
    for (JsonNode item : root) {
      if (item == null || !item.isObject()) {
        continue;
      }
      String type = item.path("type").asText(null);
      if (type == null) {
        continue;
      }
      switch (type) {
        case "TITLE", "BODY" -> {
          Map<String, Object> seg = new LinkedHashMap<>();
          seg.put("type", type);
          seg.put("pageId", pageId);
          seg.put("text", textOrEmpty(item));
          segments.add(seg);
        }
        case "DIVIDER" -> {
          Map<String, Object> seg = new LinkedHashMap<>();
          seg.put("type", "DIVIDER");
          seg.put("pageId", pageId);
          segments.add(seg);
        }
        case "BEAT" -> appendBeatSegments(segments, pageId, item);
        default -> log.debug("skip unknown content type in reading-stream: {}", type);
      }
    }
  }

  private void appendBeatSegments(
      List<Map<String, Object>> segments, Long pageId, JsonNode beat) {
    JsonNode children = beat.get("children");
    boolean hasCoverChild = false;
    if (children != null && !children.isNull() && children.isArray()) {
      for (JsonNode child : children) {
        if (child != null
            && child.isObject()
            && "COVER".equals(child.path("type").asText(null))) {
          hasCoverChild = true;
          break;
        }
      }
    }

    if (!hasCoverChild) {
      appendBeatImageFromCoverAssetId(segments, pageId, beat);
    }

    if (children == null || children.isNull() || !children.isArray()) {
      return;
    }
    for (JsonNode child : children) {
      if (child == null || !child.isObject()) {
        continue;
      }
      String childType = child.path("type").asText(null);
      if ("COVER".equals(childType)) {
        JsonNode assetIdNode = child.get("assetId");
        if (assetIdNode != null && !assetIdNode.isNull() && assetIdNode.isIntegralNumber()) {
          appendBeatImage(segments, pageId, assetIdNode.asLong());
        }
      } else if ("BODY".equals(childType) || "DIALOGUE".equals(childType)) {
        Map<String, Object> seg = new LinkedHashMap<>();
        seg.put("type", childType);
        seg.put("pageId", pageId);
        seg.put("text", textOrEmpty(child));
        segments.add(seg);
      }
    }
  }

  private void appendBeatImageFromCoverAssetId(
      List<Map<String, Object>> segments, Long pageId, JsonNode beat) {
    JsonNode coverNode = beat.get("coverAssetId");
    if (coverNode != null && !coverNode.isNull() && coverNode.isIntegralNumber()) {
      appendBeatImage(segments, pageId, coverNode.asLong());
    }
  }

  private void appendBeatImage(List<Map<String, Object>> segments, Long pageId, long assetId) {
    Map<String, Object> image = new LinkedHashMap<>();
    image.put("type", "IMAGE");
    image.put("pageId", pageId);
    image.put("assetId", assetId);
    image.put("contentPath", contentPath(assetId));
    image.put("role", "BEAT_COVER");
    segments.add(image);
  }

  private JsonNode parseContentArray(String contentJson) {
    if (contentJson == null || contentJson.isBlank()) {
      return null;
    }
    try {
      JsonNode root = objectMapper.readTree(contentJson);
      if (root == null || !root.isArray()) {
        return null;
      }
      return root;
    } catch (JsonProcessingException ex) {
      log.warn("invalid content_json in reading-stream, treating as empty: {}", ex.getMessage());
      return null;
    }
  }

  private static String textOrEmpty(JsonNode node) {
    JsonNode text = node.get("text");
    if (text == null || text.isNull()) {
      return "";
    }
    return text.asText("");
  }

  private static String contentPath(Long assetId) {
    return assetId == null ? null : "/api/assets/" + assetId + "/content";
  }

  private void applyFields(
      StoryArc arc,
      String title,
      ArcStatus status,
      String summary,
      Long coverAssetId,
      boolean create) {
    arc.setTitle(title.trim());
    if (status != null) {
      arc.setStatus(status);
    } else if (create) {
      arc.setStatus(ArcStatus.DRAFT);
    }
    arc.setSummary(blankToNull(summary));
    if (coverAssetId != null) {
      validateCoverAsset(coverAssetId);
    }
    arc.setCoverAssetId(coverAssetId);
  }

  private void validateCoverAsset(Long coverAssetId) {
    Asset asset =
        assetRepository
            .findById(coverAssetId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "cover asset not found: " + coverAssetId));
    if (asset.getStatus() != AssetStatus.NORMAL) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "cover asset is not available: " + coverAssetId);
    }
  }

  private static Specification<StoryArc> buildSpec(Long seriesId, ArcQuery query) {
    return (root, cq, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("seriesId"), seriesId));
      String keyword = trimToNull(query.q());
      if (keyword != null) {
        String pattern = "%" + keyword.toLowerCase(Locale.ROOT) + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("title")), pattern),
                cb.like(cb.lower(root.get("code")), pattern)));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }

  private String nextCode() {
    long next =
        repo.findMaxCode()
            .filter(code -> code != null && code.matches("A\\d+"))
            .map(code -> Long.parseLong(code.substring(1)) + 1)
            .orElse(1L);
    return String.format("A%06d", next);
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static String blankToNull(String value) {
    return trimToNull(value);
  }
}
