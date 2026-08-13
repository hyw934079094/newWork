package com.story.admin.dto;

public record CharacterAddFormRequest(
    String identityName, String originalFormLabel, CharacterCreateRequest newCharacter) {}
