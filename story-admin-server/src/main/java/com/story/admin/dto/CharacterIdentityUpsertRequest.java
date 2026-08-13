package com.story.admin.dto;

public record CharacterIdentityUpsertRequest(
    String name, String storyName, String publicIntro, String internalNote) {}
