package com.story.admin.service;

import com.story.admin.domain.ArcStatus;
import com.story.admin.domain.Asset;
import com.story.admin.domain.AssetStatus;
import com.story.admin.domain.StoryArc;
import com.story.admin.dto.ArcCreateRequest;
import com.story.admin.dto.ArcQuery;
import com.story.admin.dto.ArcUpdateRequest;
import com.story.admin.repository.AssetRepository;
import com.story.admin.repository.StoryArcRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ArcService {

  private final StoryArcRepository repo;
  private final SeriesService seriesService;
  private final AssetRepository assetRepository;

  public ArcService(
      StoryArcRepository repo, SeriesService seriesService, AssetRepository assetRepository) {
    this.repo = repo;
    this.seriesService = seriesService;
    this.assetRepository = assetRepository;
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
    repo.deleteById(id);
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
