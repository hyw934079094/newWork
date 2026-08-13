package com.story.admin.dto;

public record CharacterUpdateRequest(
    String name,
    String alias,
    String gender,
    String ageStage,
    String race,
    String occupation,
    String publicIntro,
    String internalNote) {}
