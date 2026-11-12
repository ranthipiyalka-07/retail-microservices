package com.retail.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TemplateUpdateRequest(
        @NotBlank @Size(max = 200) String subject,
        @NotBlank @Size(max = 5000) String body,
        Boolean active) {}                                     // empty = keep as it is