package com.story.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ComboDetailResponse(
    Long id,
    String name,
    String playSequence,
    BigDecimal defaultIntervalSec,
    boolean loopEnabled,
    String remark,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<MemberView> members,
    List<StepHoldView> stepHolds) {

  public record MemberView(
      int memberNo, Long assetId, String displayName, String contentUrl, String contentType) {}

  public record StepHoldView(int stepIndex, BigDecimal holdSeconds) {}
}
