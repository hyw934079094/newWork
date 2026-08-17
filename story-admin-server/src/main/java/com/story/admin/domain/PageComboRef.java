package com.story.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "page_combo_ref")
@IdClass(PageComboRefId.class)
public class PageComboRef {

  @Id
  @Column(name = "page_id", nullable = false)
  private Long pageId;

  @Id
  @Column(name = "combo_id", nullable = false)
  private Long comboId;

  public PageComboRef() {}

  public PageComboRef(Long pageId, Long comboId) {
    this.pageId = pageId;
    this.comboId = comboId;
  }

  public Long getPageId() {
    return pageId;
  }

  public void setPageId(Long pageId) {
    this.pageId = pageId;
  }

  public Long getComboId() {
    return comboId;
  }

  public void setComboId(Long comboId) {
    this.comboId = comboId;
  }
}
