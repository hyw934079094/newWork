package com.story.admin.dto;

import java.math.BigDecimal;

public record AiReferenceItemRequest(
    Long assetId, String purpose, String note, BigDecimal strength) {}
