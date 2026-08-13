package com.story.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "asset_combo_step_hold")
public class AssetComboStepHold {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "combo_id", nullable = false)
  private Long comboId;

  @Column(name = "step_index", nullable = false)
  private int stepIndex;

  @Column(name = "hold_seconds", nullable = false, precision = 10, scale = 2)
  private BigDecimal holdSeconds;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getComboId() {
    return comboId;
  }

  public void setComboId(Long comboId) {
    this.comboId = comboId;
  }

  public int getStepIndex() {
    return stepIndex;
  }

  public void setStepIndex(int stepIndex) {
    this.stepIndex = stepIndex;
  }

  public BigDecimal getHoldSeconds() {
    return holdSeconds;
  }

  public void setHoldSeconds(BigDecimal holdSeconds) {
    this.holdSeconds = holdSeconds;
  }
}
