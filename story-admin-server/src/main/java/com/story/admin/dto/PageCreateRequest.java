package com.story.admin.dto;

public record PageCreateRequest(String title, Integer sortOrder) {

  public PageCreateRequest(String title) {
    this(title, null);
  }
}
