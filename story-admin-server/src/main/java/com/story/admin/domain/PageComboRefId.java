package com.story.admin.domain;

import java.io.Serializable;
import java.util.Objects;

public class PageComboRefId implements Serializable {

  private Long pageId;
  private Long comboId;

  public PageComboRefId() {}

  public PageComboRefId(Long pageId, Long comboId) {
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof PageComboRefId that)) {
      return false;
    }
    return Objects.equals(pageId, that.pageId) && Objects.equals(comboId, that.comboId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(pageId, comboId);
  }
}
