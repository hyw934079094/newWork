package com.story.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "asset_combo")
public class AssetCombo {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 255)
  private String name;

  @Column(name = "play_sequence", nullable = false, length = 1000)
  private String playSequence;

  @Column(name = "default_interval_sec", nullable = false, precision = 10, scale = 2)
  private BigDecimal defaultIntervalSec = new BigDecimal("1.00");

  @Column(name = "loop_enabled", nullable = false)
  private boolean loopEnabled = true;

  @Column(length = 500)
  private String remark;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = LocalDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getPlaySequence() {
    return playSequence;
  }

  public void setPlaySequence(String playSequence) {
    this.playSequence = playSequence;
  }

  public BigDecimal getDefaultIntervalSec() {
    return defaultIntervalSec;
  }

  public void setDefaultIntervalSec(BigDecimal defaultIntervalSec) {
    this.defaultIntervalSec = defaultIntervalSec;
  }

  public boolean isLoopEnabled() {
    return loopEnabled;
  }

  public void setLoopEnabled(boolean loopEnabled) {
    this.loopEnabled = loopEnabled;
  }

  public String getRemark() {
    return remark;
  }

  public void setRemark(String remark) {
    this.remark = remark;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
