package com.story.admin.dto;

import java.math.BigDecimal;

public record ComboStepHoldRequest(Integer stepIndex, BigDecimal holdSeconds) {}
