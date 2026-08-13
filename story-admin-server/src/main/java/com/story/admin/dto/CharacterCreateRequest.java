package com.story.admin.dto;

public record CharacterCreateRequest(
    String name,
    String alias,
    String gender,
    String ageStage,
    String race,
    String occupation,
    String publicIntro,
    String internalNote) {}
