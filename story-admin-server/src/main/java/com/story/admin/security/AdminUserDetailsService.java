package com.story.admin.security;

import com.story.admin.domain.AdminUser;
import com.story.admin.repository.AdminUserRepository;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AdminUserDetailsService implements UserDetailsService {

  private final AdminUserRepository repo;

  public AdminUserDetailsService(AdminUserRepository repo) {
    this.repo = repo;
  }

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    AdminUser user =
        repo.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("user not found: " + username));
    return User.builder()
        .username(user.getUsername())
        .password(user.getPasswordHash())
        .disabled(!user.isEnabled())
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        .build();
  }
}
