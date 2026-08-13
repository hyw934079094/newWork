package com.story.admin.service;

import com.story.admin.domain.Asset;
import com.story.admin.domain.AssetCombo;
import com.story.admin.domain.AssetComboMember;
import com.story.admin.domain.AssetComboStepHold;
import com.story.admin.domain.AssetStatus;
import com.story.admin.dto.ComboDetailResponse;
import com.story.admin.dto.ComboMemberRequest;
import com.story.admin.dto.ComboStepHoldRequest;
import com.story.admin.dto.ComboUpsertRequest;
import com.story.admin.repository.AssetComboMemberRepository;
import com.story.admin.repository.AssetComboRepository;
import com.story.admin.repository.AssetComboStepHoldRepository;
import com.story.admin.repository.AssetRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
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
public class ComboService {

  private static final BigDecimal MIN_SECONDS = new BigDecimal("0.1");

  private final AssetComboRepository comboRepository;
  private final AssetComboMemberRepository memberRepository;
  private final AssetComboStepHoldRepository stepHoldRepository;
  private final AssetRepository assetRepository;

  public ComboService(
      AssetComboRepository comboRepository,
      AssetComboMemberRepository memberRepository,
      AssetComboStepHoldRepository stepHoldRepository,
      AssetRepository assetRepository) {
    this.comboRepository = comboRepository;
    this.memberRepository = memberRepository;
    this.stepHoldRepository = stepHoldRepository;
    this.assetRepository = assetRepository;
  }

  public List<ComboDetailResponse> list() {
    return comboRepository.findAllByOrderByUpdatedAtDescIdDesc().stream()
        .map(this::toDetail)
        .toList();
  }

  public ComboDetailResponse get(Long id) {
    return toDetail(requireCombo(id));
  }

  @Transactional
  public ComboDetailResponse create(ComboUpsertRequest req) {
    return upsert(null, req);
  }

  @Transactional
  public ComboDetailResponse update(Long id, ComboUpsertRequest req) {
    requireCombo(id);
    return upsert(id, req);
  }

  @Transactional
  public void delete(Long id) {
    requireCombo(id);
    stepHoldRepository.deleteByComboId(id);
    memberRepository.deleteByComboId(id);
    comboRepository.deleteById(id);
  }

  private ComboDetailResponse upsert(Long id, ComboUpsertRequest req) {
    if (req == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "body is required");
    }
    if (req.name() == null || req.name().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
    }
    if (req.defaultIntervalSec() == null || req.defaultIntervalSec().compareTo(MIN_SECONDS) < 0) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "defaultIntervalSec must be >= 0.1");
    }
    List<ComboMemberRequest> members = req.members() != null ? req.members() : List.of();
    if (members.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "at least one member is required");
    }
    if (req.playSequence() == null || req.playSequence().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "playSequence is required");
    }

    validateMemberNosIfProvided(members);
    List<Long> assetIds = new ArrayList<>(members.size());
    Set<Long> seenAssets = new HashSet<>();
    for (ComboMemberRequest member : members) {
      if (member == null || member.assetId() == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "member assetId is required");
      }
      if (!seenAssets.add(member.assetId())) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "duplicate assetId in members: " + member.assetId());
      }
      Asset asset =
          assetRepository
              .findById(member.assetId())
              .orElseThrow(
                  () ->
                      new ResponseStatusException(
                          HttpStatus.BAD_REQUEST, "asset not found: " + member.assetId()));
      if (asset.getStatus() != AssetStatus.NORMAL) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "asset is not available: " + member.assetId());
      }
      assetIds.add(member.assetId());
    }

    Set<Integer> memberNos = new HashSet<>();
    for (int i = 0; i < members.size(); i++) {
      memberNos.add(i + 1);
    }

    List<Integer> steps = parsePlaySequence(req.playSequence());
    if (steps.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "playSequence is required");
    }
    for (Integer step : steps) {
      if (!memberNos.contains(step)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "playSequence contains unknown memberNo: " + step);
      }
    }

    List<ComboStepHoldRequest> holds = req.stepHolds() != null ? req.stepHolds() : List.of();
    Set<Integer> seenSteps = new HashSet<>();
    for (ComboStepHoldRequest hold : holds) {
      if (hold == null || hold.stepIndex() == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stepIndex is required");
      }
      if (hold.stepIndex() < 1 || hold.stepIndex() > steps.size()) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "stepIndex out of range: " + hold.stepIndex() + " (sequence length " + steps.size() + ")");
      }
      if (!seenSteps.add(hold.stepIndex())) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "duplicate stepIndex: " + hold.stepIndex());
      }
      if (hold.holdSeconds() == null || hold.holdSeconds().compareTo(MIN_SECONDS) < 0) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "holdSeconds must be >= 0.1");
      }
    }

    AssetCombo combo;
    if (id == null) {
      combo = new AssetCombo();
    } else {
      combo = requireCombo(id);
      memberRepository.deleteByComboId(id);
      stepHoldRepository.deleteByComboId(id);
      memberRepository.flush();
      stepHoldRepository.flush();
    }

    combo.setName(req.name().trim());
    combo.setPlaySequence(normalizePlaySequence(steps));
    combo.setDefaultIntervalSec(req.defaultIntervalSec());
    combo.setLoopEnabled(req.loopEnabled() == null || req.loopEnabled());
    combo.setRemark(req.remark());
    combo = comboRepository.save(combo);

    for (int i = 0; i < assetIds.size(); i++) {
      AssetComboMember member = new AssetComboMember();
      member.setComboId(combo.getId());
      member.setAssetId(assetIds.get(i));
      member.setMemberNo(i + 1);
      member.setSortOrder(i);
      memberRepository.save(member);
    }

    for (ComboStepHoldRequest hold : holds) {
      AssetComboStepHold entity = new AssetComboStepHold();
      entity.setComboId(combo.getId());
      entity.setStepIndex(hold.stepIndex());
      entity.setHoldSeconds(hold.holdSeconds());
      stepHoldRepository.save(entity);
    }

    return toDetail(combo);
  }

  private void validateMemberNosIfProvided(List<ComboMemberRequest> members) {
    boolean anyProvided = members.stream().anyMatch(m -> m != null && m.memberNo() != null);
    if (!anyProvided) {
      return;
    }
    for (int i = 0; i < members.size(); i++) {
      ComboMemberRequest member = members.get(i);
      if (member == null || member.memberNo() == null) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "memberNo must be continuous 1..n when provided");
      }
      if (member.memberNo() != i + 1) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "memberNo must be continuous 1..n in submission order");
      }
    }
  }

  private List<Integer> parsePlaySequence(String playSequence) {
    String[] parts = playSequence.split(",");
    List<Integer> steps = new ArrayList<>();
    for (String part : parts) {
      String token = part.trim();
      if (token.isEmpty()) {
        continue;
      }
      try {
        int value = Integer.parseInt(token);
        if (value < 1) {
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST, "playSequence items must be positive integers");
        }
        steps.add(value);
      } catch (NumberFormatException ex) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "playSequence items must be positive integers");
      }
    }
    return steps;
  }

  private static String normalizePlaySequence(List<Integer> steps) {
    return steps.stream().map(String::valueOf).collect(Collectors.joining(","));
  }

  private AssetCombo requireCombo(Long id) {
    return comboRepository
        .findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "combo not found: " + id));
  }

  private ComboDetailResponse toDetail(AssetCombo combo) {
    List<AssetComboMember> members =
        memberRepository.findByComboIdOrderBySortOrderAscMemberNoAsc(combo.getId());
    List<AssetComboStepHold> holds =
        stepHoldRepository.findByComboIdOrderByStepIndexAsc(combo.getId());

    Set<Long> assetIds =
        members.stream().map(AssetComboMember::getAssetId).collect(Collectors.toSet());
    Map<Long, Asset> assetsById =
        assetRepository.findAllById(assetIds).stream()
            .collect(Collectors.toMap(Asset::getId, Function.identity()));

    List<ComboDetailResponse.MemberView> memberViews = new ArrayList<>(members.size());
    for (AssetComboMember member : members) {
      Asset asset = assetsById.get(member.getAssetId());
      String displayName = asset != null ? asset.getDisplayName() : null;
      String contentType = asset != null ? asset.getContentType() : null;
      String contentUrl = "/api/assets/" + member.getAssetId() + "/content";
      memberViews.add(
          new ComboDetailResponse.MemberView(
              member.getMemberNo(),
              member.getAssetId(),
              displayName,
              contentUrl,
              contentType));
    }

    List<ComboDetailResponse.StepHoldView> holdViews =
        holds.stream()
            .map(h -> new ComboDetailResponse.StepHoldView(h.getStepIndex(), h.getHoldSeconds()))
            .toList();

    return new ComboDetailResponse(
        combo.getId(),
        combo.getName(),
        combo.getPlaySequence(),
        combo.getDefaultIntervalSec(),
        combo.isLoopEnabled(),
        combo.getRemark(),
        combo.getCreatedAt(),
        combo.getUpdatedAt(),
        memberViews,
        holdViews);
  }
}
