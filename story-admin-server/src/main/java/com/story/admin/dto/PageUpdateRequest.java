package com.story.admin.dto;

public record PageUpdateRequest(String title, String contentJson, Integer sortOrder) {

  public PageUpdateRequest(String title, String contentJson) {
    this(title, contentJson, null);
  }
}
