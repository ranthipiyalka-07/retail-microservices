package com.retail.notification.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** type is an EventType name, for example ORDER_CREATED. payload is the event as JSON. */
public record EventEnvelope(@NotBlank @Size(max = 40) String type, @NotNull JsonNode payload) {}