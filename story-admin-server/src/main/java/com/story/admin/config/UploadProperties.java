package com.story.admin.config;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "story.upload")
public class UploadProperties {

  private int maxFileSizeMb = 20;
  private String allowedExtensions = "jpg,jpeg,png,webp,gif";

  public int getMaxFileSizeMb() {
    return maxFileSizeMb;
  }

  public void setMaxFileSizeMb(int maxFileSizeMb) {
    this.maxFileSizeMb = maxFileSizeMb;
  }

  public String getAllowedExtensions() {
    return allowedExtensions;
  }

  public void setAllowedExtensions(String allowedExtensions) {
    this.allowedExtensions = allowedExtensions;
  }

  public Set<String> allowedExtensionSet() {
    return Arrays.stream(allowedExtensions.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(s -> s.toLowerCase(Locale.ROOT))
        .collect(Collectors.toSet());
  }

  public long maxFileSizeBytes() {
    return maxFileSizeMb * 1024L * 1024L;
  }

  public List<String> allowedExtensionList() {
    return List.copyOf(allowedExtensionSet());
  }
}
