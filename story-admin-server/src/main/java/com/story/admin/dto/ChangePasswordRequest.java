package com.story.admin.dto;

public record ChangePasswordRequest(String currentPassword, String newPassword) {}
