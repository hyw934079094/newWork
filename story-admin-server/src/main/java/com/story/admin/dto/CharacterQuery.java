package com.story.admin.dto;

/** 人物列表筛选：关键词匹配姓名/别名/编号；故事、性别等按字段模糊或精确过滤。 */
public record CharacterQuery(
    String q, String storyName, String gender, String ageStage, String race, String occupation) {}
