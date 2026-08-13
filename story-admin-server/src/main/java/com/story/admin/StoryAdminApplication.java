package com.story.admin;

import com.story.admin.config.StorageProperties;
import com.story.admin.config.UploadProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({StorageProperties.class, UploadProperties.class})
public class StoryAdminApplication {
  public static void main(String[] args) {
    SpringApplication.run(StoryAdminApplication.class, args);
  }
}
