package com.story.admin.service;

import com.story.admin.domain.Asset;
import com.story.admin.domain.AssetStatus;
import com.story.admin.domain.SeriesStatus;
import com.story.admin.domain.StorySeries;
import com.story.admin.dto.SeriesCreateRequest;
import com.story.admin.dto.SeriesQuery;
import com.story.admin.dto.SeriesUpdateRequest;
import com.story.admin.repository.AssetRepository;
import com.story.admin.repository.StorySeriesRepository;
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
public class SeriesService {

  private final StorySeriesRepository repo;
  private final AssetRepository assetRepository;

  public SeriesService(StorySeriesRepository repo, AssetRepository assetRepository) {
    this.repo = repo;
    this.assetRepository = assetRepository;
  }

  public List<StorySeries> list(SeriesQuery query) {
    SeriesQuery q = query == null ? new SeriesQuery(null, null) : query;
    return repo.findAll(
        buildSpec(q), Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("id")));
  }

  public StorySeries get(Long id) {
    return repo.findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "series not found: " + id));
  }

  @Transactional
  public StorySeries create(SeriesCreateRequest req) {
    if (req == null || req.name() == null || req.name().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "series name is required");
    }
    StorySeries series = new StorySeries();
    series.setCode(nextCode());
    series.setSortOrder(0);
    applyFields(series, req.name(), req.status(), req.summary(), req.tags(), req.coverAssetId(), true);
    return repo.save(series);
  }

  @Transactional
  public StorySeries update(Long id, SeriesUpdateRequest req) {
    if (req == null || req.name() == null || req.name().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "series name is required");
    }
    StorySeries series = get(id);
    applyFields(series, req.name(), req.status(), req.summary(), req.tags(), req.coverAssetId(), false);
    return repo.save(series);
  }

  @Transactional
  public void delete(Long id) {
    if (!repo.existsById(id)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "series not found: " + id);
    }
    repo.deleteById(id);
  }

  private void applyFields(
      StorySeries series,
      String name,
      SeriesStatus status,
      String summary,
      String tags,
      Long coverAssetId,
      boolean create) {
    series.setName(name.trim());
    if (status != null) {
      series.setStatus(status);
    } else if (create) {
      series.setStatus(SeriesStatus.DRAFT);
    }
    series.setSummary(blankToNull(summary));
    series.setTags(blankToNull(tags));
    if (coverAssetId != null) {
      validateCoverAsset(coverAssetId);
    }
    series.setCoverAssetId(coverAssetId);
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

  private static Specification<StorySeries> buildSpec(SeriesQuery query) {
    return (root, cq, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      String keyword = trimToNull(query.q());
      if (keyword != null) {
        String pattern = "%" + keyword.toLowerCase(Locale.ROOT) + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(cb.lower(root.get("code")), pattern),
                cb.like(cb.lower(cb.coalesce(root.get("tags"), "")), pattern)));
      }
      if (query.status() != null) {
        predicates.add(cb.equal(root.get("status"), query.status()));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }

  private String nextCode() {
    long next =
        repo.findMaxCode()
            .filter(code -> code != null && code.matches("S\\d+"))
            .map(code -> Long.parseLong(code.substring(1)) + 1)
            .orElse(1L);
    return String.format("S%06d", next);
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
