package com.story.admin.dto;

import java.math.BigDecimal;
import java.util.List;

public record ComboUpsertRequest(
    String name,
    String playSequence,
    BigDecimal defaultIntervalSec,
    Boolean loopEnabled,
    String remark,
    List<ComboMemberRequest> members,
    List<ComboStepHoldRequest> stepHolds) {}
