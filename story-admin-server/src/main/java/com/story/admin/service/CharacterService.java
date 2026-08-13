package com.story.admin.service;

import com.story.admin.domain.Asset;
import com.story.admin.domain.AssetCategory;
import com.story.admin.domain.AssetCharacterRel;
import com.story.admin.domain.AssetStatus;
import com.story.admin.domain.CharacterProfile;
import com.story.admin.dto.CharacterCreateRequest;
import com.story.admin.dto.CharacterQuery;
import com.story.admin.dto.CharacterUpdateRequest;
import com.story.admin.exception.ConflictException;
import com.story.admin.repository.AssetCategoryRepository;
import com.story.admin.repository.AssetCharacterRelRepository;
import com.story.admin.repository.AssetRepository;
import com.story.admin.repository.CharacterProfileRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CharacterService {

  private final CharacterProfileRepository repo;
  private final AssetCharacterRelRepository characterRelRepository;
  private final AssetRepository assetRepository;
  private final AssetCategoryRepository categoryRepository;
  private final AssetService assetService;

  public CharacterService(
      CharacterProfileRepository repo,
      AssetCharacterRelRepository characterRelRepository,
      AssetRepository assetRepository,
      AssetCategoryRepository categoryRepository,
      AssetService assetService) {
    this.repo = repo;
    this.characterRelRepository = characterRelRepository;
    this.assetRepository = assetRepository;
    this.categoryRepository = categoryRepository;
    this.assetService = assetService;
  }

  public List<CharacterProfile> list() {
    return list(new CharacterQuery(null, null, null, null, null, null));
  }

  public List<CharacterProfile> list(CharacterQuery query) {
    CharacterQuery q = query == null ? new CharacterQuery(null, null, null, null, null, null) : query;
    return repo.findAll(buildSpec(q), Sort.by(Sort.Direction.ASC, "code"));
  }

  public CharacterProfile get(Long id) {
    return repo.findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "character not found: " + id));
  }

  @Transactional
  public CharacterProfile create(CharacterCreateRequest req) {
    if (req == null || req.name() == null || req.name().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "character name is required");
    }
    CharacterProfile profile = new CharacterProfile();
    profile.setCode(nextCode());
    applyFields(
        profile,
        req.name(),
        req.alias(),
        req.gender(),
        req.ageStage(),
        req.race(),
        req.occupation(),
        req.storyName(),
        req.publicIntro(),
        req.internalNote());
    return repo.save(profile);
  }

  @Transactional
  public CharacterProfile update(Long id, CharacterUpdateRequest req) {
    if (req == null || req.name() == null || req.name().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "character name is required");
    }
    CharacterProfile profile = get(id);
    applyFields(
        profile,
        req.name(),
        req.alias(),
        req.gender(),
        req.ageStage(),
        req.race(),
        req.occupation(),
        req.storyName(),
        req.publicIntro(),
        req.internalNote());
    return repo.save(profile);
  }

  @Transactional
  public void delete(Long id) {
    if (!repo.existsById(id)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "character not found: " + id);
    }
    List<Long> assetIds = characterRelRepository.findAssetIdsByCharacterId(id);
    if (!assetIds.isEmpty()) {
      throw new ConflictException(buildLinkedAssetSummary(assetIds));
    }
    repo.deleteById(id);
  }

  public List<Asset> listAssets(Long characterId) {
    get(characterId);
    List<Long> assetIds = characterRelRepository.findAssetIdsByCharacterId(characterId);
    if (assetIds.isEmpty()) {
      return List.of();
    }
    Map<Long, Asset> byId =
        assetRepository.findAllById(assetIds).stream()
            .filter(asset -> asset.getStatus() == AssetStatus.NORMAL)
            .collect(Collectors.toMap(Asset::getId, Function.identity()));
    List<Asset> ordered = new ArrayList<>();
    for (Long assetId : assetIds) {
      Asset asset = byId.get(assetId);
      if (asset != null) {
        ordered.add(assetService.get(asset.getId()));
      }
    }
    return ordered;
  }

  @Transactional
  public List<Asset> replaceAssets(Long characterId, List<Long> assetIds) {
    get(characterId);
    LinkedHashSet<Long> unique = new LinkedHashSet<>();
    if (assetIds != null) {
      for (Long assetId : assetIds) {
        if (assetId != null) {
          unique.add(assetId);
        }
      }
    }
    for (Long assetId : unique) {
      Asset asset =
          assetRepository
              .findById(assetId)
              .orElseThrow(
                  () ->
                      new ResponseStatusException(
                          HttpStatus.BAD_REQUEST, "asset not found: " + assetId));
      if (asset.getStatus() != AssetStatus.NORMAL) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "asset is not available: " + assetId);
      }
    }
    characterRelRepository.deleteByCharacterId(characterId);
    characterRelRepository.flush();
    for (Long assetId : unique) {
      characterRelRepository.save(new AssetCharacterRel(assetId, characterId));
    }
    return listAssets(characterId);
  }

  @Transactional
  public List<Asset> uploadAssets(Long characterId, Long categoryId, MultipartFile[] files) {
    get(characterId);
    Long resolvedCategoryId = categoryId != null ? categoryId : defaultPortraitCategoryId();
    List<Asset> uploaded = assetService.upload(resolvedCategoryId, files);
    List<Long> current = new ArrayList<>(characterRelRepository.findAssetIdsByCharacterId(characterId));
    for (Asset asset : uploaded) {
      if (!current.contains(asset.getId())) {
        current.add(asset.getId());
      }
    }
    return replaceAssets(characterId, current);
  }

  private Long defaultPortraitCategoryId() {
    return categoryRepository
        .findByCode("portrait")
        .map(AssetCategory::getId)
        .or(
            () ->
                categoryRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                    .findFirst()
                    .map(AssetCategory::getId))
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "no asset category available for upload"));
  }

  private static Specification<CharacterProfile> buildSpec(CharacterQuery query) {
    return (root, cq, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      String keyword = trimToNull(query.q());
      if (keyword != null) {
        String pattern = "%" + keyword.toLowerCase(Locale.ROOT) + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(cb.lower(cb.coalesce(root.get("alias"), "")), pattern),
                cb.like(cb.lower(root.get("code")), pattern),
                cb.like(cb.lower(cb.coalesce(root.get("occupation"), "")), pattern)));
      }
      String storyName = trimToNull(query.storyName());
      if (storyName != null) {
        predicates.add(
            cb.like(
                cb.lower(cb.coalesce(root.get("storyName"), "")),
                "%" + storyName.toLowerCase(Locale.ROOT) + "%"));
      }
      String gender = trimToNull(query.gender());
      if (gender != null) {
        predicates.add(
            cb.like(
                cb.lower(cb.coalesce(root.get("gender"), "")),
                "%" + gender.toLowerCase(Locale.ROOT) + "%"));
      }
      String ageStage = trimToNull(query.ageStage());
      if (ageStage != null) {
        predicates.add(
            cb.like(
                cb.lower(cb.coalesce(root.get("ageStage"), "")),
                "%" + ageStage.toLowerCase(Locale.ROOT) + "%"));
      }
      String race = trimToNull(query.race());
      if (race != null) {
        predicates.add(
            cb.like(
                cb.lower(cb.coalesce(root.get("race"), "")),
                "%" + race.toLowerCase(Locale.ROOT) + "%"));
      }
      String occupation = trimToNull(query.occupation());
      if (occupation != null) {
        predicates.add(
            cb.like(
                cb.lower(cb.coalesce(root.get("occupation"), "")),
                "%" + occupation.toLowerCase(Locale.ROOT) + "%"));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static String buildLinkedAssetSummary(List<Long> assetIds) {
    String ids = assetIds.stream().map(String::valueOf).collect(Collectors.joining(", "));
    return "无法删除人物：仍存在素材关联。关联素材("
        + assetIds.size()
        + "): ["
        + ids
        + "].";
  }

  private void applyFields(
      CharacterProfile profile,
      String name,
      String alias,
      String gender,
      String ageStage,
      String race,
      String occupation,
      String storyName,
      String publicIntro,
      String internalNote) {
    profile.setName(name.trim());
    profile.setAlias(blankToNull(alias));
    profile.setGender(blankToNull(gender));
    profile.setAgeStage(blankToNull(ageStage));
    profile.setRace(blankToNull(race));
    profile.setOccupation(blankToNull(occupation));
    profile.setStoryName(blankToNull(storyName));
    profile.setPublicIntro(blankToNull(publicIntro));
    profile.setInternalNote(blankToNull(internalNote));
  }

  private static String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private String nextCode() {
    long next =
        repo.findMaxCode()
            .filter(code -> code != null && code.matches("C\\d+"))
            .map(code -> Long.parseLong(code.substring(1)) + 1)
            .orElse(1L);
    return String.format("C%06d", next);
  }
}
