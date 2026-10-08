package ru.yandex.practicum.order.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InventoryResponse(

        Boolean success,

        Integer availableQuantity,

        String message
) {
}
