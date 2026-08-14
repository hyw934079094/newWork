package com.story.admin.security;

import com.story.admin.domain.AdminUser;
import com.story.admin.repository.AdminUserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminUserSeeder implements ApplicationRunner {

  public static final String DEFAULT_USERNAME = "admin";
  public static final String DEFAULT_PASSWORD = "admin";

  private final AdminUserRepository repo;
  private final PasswordEncoder passwordEncoder;

  public AdminUserSeeder(AdminUserRepository repo, PasswordEncoder passwordEncoder) {
    this.repo = repo;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (repo.existsByUsername(DEFAULT_USERNAME)) {
      return;
    }
    AdminUser user = new AdminUser();
    user.setUsername(DEFAULT_USERNAME);
    user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
    user.setDisplayName("超级管理员");
    user.setEnabled(true);
    repo.save(user);
  }
}
