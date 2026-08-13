package com.story.admin.service;

import com.story.admin.domain.Asset;
import com.story.admin.domain.AssetStatus;
import com.story.admin.domain.CharacterIdentity;
import com.story.admin.domain.CharacterProfile;
import com.story.admin.domain.IdentityAssetRel;
import com.story.admin.dto.CharacterIdentityUpsertRequest;
import com.story.admin.dto.IdentityDetailResponse;
import com.story.admin.dto.IdentityMemberRequest;
import com.story.admin.exception.ConflictException;
import com.story.admin.repository.AssetCharacterRelRepository;
import com.story.admin.repository.AssetRepository;
import com.story.admin.repository.CharacterIdentityRepository;
import com.story.admin.repository.CharacterProfileRepository;
import com.story.admin.repository.IdentityAssetRelRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CharacterIdentityService {

  private final CharacterIdentityRepository identityRepository;
  private final CharacterProfileRepository characterProfileRepository;
  private final IdentityAssetRelRepository identityAssetRelRepository;
  private final AssetRepository assetRepository;
  private final AssetCharacterRelRepository characterRelRepository;

  public CharacterIdentityService(
      CharacterIdentityRepository identityRepository,
      CharacterProfileRepository characterProfileRepository,
      IdentityAssetRelRepository identityAssetRelRepository,
      AssetRepository assetRepository,
      AssetCharacterRelRepository characterRelRepository) {
    this.identityRepository = identityRepository;
    this.characterProfileRepository = characterProfileRepository;
    this.identityAssetRelRepository = identityAssetRelRepository;
    this.assetRepository = assetRepository;
    this.characterRelRepository = characterRelRepository;
  }

  public List<IdentityDetailResponse> list() {
    return identityRepository.findAllByOrderByUpdatedAtDescIdDesc().stream()
        .map(this::toDetail)
        .toList();
  }

  public IdentityDetailResponse get(Long id) {
    return toDetail(requireIdentity(id));
  }

  @Transactional
  public IdentityDetailResponse create(CharacterIdentityUpsertRequest req) {
    return upsert(null, req);
  }

  @Transactional
  public IdentityDetailResponse update(Long id, CharacterIdentityUpsertRequest req) {
    requireIdentity(id);
    return upsert(id, req);
  }

  @Transactional
  public void delete(Long id) {
    requireIdentity(id);
    List<CharacterProfile> forms = characterProfileRepository.findByIdentityIdOrderByIdAsc(id);
    if (!forms.isEmpty()) {
      String names =
          forms.stream().map(CharacterProfile::getName).collect(Collectors.joining(", "));
      throw new ConflictException("无法删除人物本体：仍存在形态挂接。形态: [" + names + "].");
    }
    identityAssetRelRepository.deleteByIdentityId(id);
    identityAssetRelRepository.flush();
    identityRepository.deleteById(id);
  }

  @Transactional
  public IdentityDetailResponse setMembers(Long id, List<IdentityMemberRequest> members) {
    CharacterIdentity identity = requireIdentity(id);
    List<IdentityMemberRequest> requested = members == null ? List.of() : members;

    LinkedHashSet<Long> newIds = new LinkedHashSet<>();
    for (IdentityMemberRequest member : requested) {
      if (member == null || member.characterId() == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "characterId is required");
      }
      if (!newIds.add(member.characterId())) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "duplicate characterId: " + member.characterId());
      }
    }

    List<CharacterProfile> current = characterProfileRepository.findByIdentityIdOrderByIdAsc(id);
    for (CharacterProfile profile : current) {
      if (!newIds.contains(profile.getId())) {
        profile.setIdentityId(null);
        profile.setFormLabel(null);
        characterProfileRepository.save(profile);
      }
    }

    for (IdentityMemberRequest member : requested) {
      CharacterProfile profile =
          characterProfileRepository
              .findById(member.characterId())
              .orElseThrow(
                  () ->
                      new ResponseStatusException(
                          HttpStatus.BAD_REQUEST, "character not found: " + member.characterId()));
      if (profile.getIdentityId() != null && !Objects.equals(profile.getIdentityId(), id)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "character already belongs to another identity: " + member.characterId());
      }
      profile.setIdentityId(identity.getId());
      profile.setFormLabel(blankToNull(member.formLabel()));
      characterProfileRepository.save(profile);
    }

    return toDetail(identity);
  }

  @Transactional
  public IdentityDetailResponse setAssets(Long id, List<Long> assetIds) {
    CharacterIdentity identity = requireIdentity(id);
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
    identityAssetRelRepository.deleteByIdentityId(id);
    identityAssetRelRepository.flush();
    for (Long assetId : unique) {
      identityAssetRelRepository.save(new IdentityAssetRel(id, assetId));
    }
    return toDetail(identity);
  }

  private IdentityDetailResponse upsert(Long id, CharacterIdentityUpsertRequest req) {
    if (req == null || req.name() == null || req.name().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
    }
    CharacterIdentity identity;
    if (id == null) {
      identity = new CharacterIdentity();
      identity.setCode(nextIdentityCode());
    } else {
      identity = requireIdentity(id);
    }
    identity.setName(req.name().trim());
    identity.setStoryName(blankToNull(req.storyName()));
    identity.setPublicIntro(blankToNull(req.publicIntro()));
    identity.setInternalNote(blankToNull(req.internalNote()));
    return toDetail(identityRepository.save(identity));
  }

  private CharacterIdentity requireIdentity(Long id) {
    return identityRepository
        .findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "identity not found: " + id));
  }

  private IdentityDetailResponse toDetail(CharacterIdentity identity) {
    List<CharacterProfile> forms =
        characterProfileRepository.findByIdentityIdOrderByIdAsc(identity.getId());
    List<Long> assetIds = identityAssetRelRepository.findAssetIdsByIdentityId(identity.getId());
    Map<Long, Asset> assetsById =
        assetRepository.findAllById(assetIds).stream()
            .filter(asset -> asset.getStatus() == AssetStatus.NORMAL)
            .collect(Collectors.toMap(Asset::getId, Function.identity()));

    List<IdentityDetailResponse.MemberView> memberViews = new ArrayList<>(forms.size());
    for (CharacterProfile form : forms) {
      int assetCount = characterRelRepository.findAssetIdsByCharacterId(form.getId()).size();
      memberViews.add(
          new IdentityDetailResponse.MemberView(
              form.getId(), form.getCode(), form.getName(), form.getFormLabel(), assetCount));
    }

    // Rel rows for DELETED may remain until next setAssets; never expose non-NORMAL to the editor.
    List<IdentityDetailResponse.AssetView> assetViews = new ArrayList<>(assetIds.size());
    for (Long assetId : assetIds) {
      Asset asset = assetsById.get(assetId);
      if (asset == null) {
        continue;
      }
      assetViews.add(
          new IdentityDetailResponse.AssetView(
              asset.getId(),
              asset.getDisplayName(),
              "/api/assets/" + asset.getId() + "/content",
              asset.getContentType()));
    }

    return new IdentityDetailResponse(
        identity.getId(),
        identity.getCode(),
        identity.getName(),
        identity.getStoryName(),
        identity.getPublicIntro(),
        identity.getInternalNote(),
        identity.getCreatedAt(),
        identity.getUpdatedAt(),
        memberViews.size(),
        memberViews,
        assetViews);
  }

  private String nextIdentityCode() {
    long next =
        identityRepository
            .findMaxCode()
            .filter(code -> code != null && code.matches("ID-\\d+"))
            .map(code -> Long.parseLong(code.substring(3)) + 1)
            .orElse(1L);
    return String.format("ID-%04d", next);
  }

  private static String blankToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
