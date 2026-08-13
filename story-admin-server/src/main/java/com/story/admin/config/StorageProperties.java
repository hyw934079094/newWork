package com.story.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "story.storage")
public class StorageProperties {

  private String root = "../storage";

  public String getRoot() {
    return root;
  }

  public void setRoot(String root) {
    this.root = root;
  }
}
