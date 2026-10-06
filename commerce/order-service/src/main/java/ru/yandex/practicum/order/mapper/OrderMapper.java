package ru.yandex.practicum.order.mapper;

import org.springframework.stereotype.Component;
import ru.yandex.practicum.order.dto.OrderDto;
import ru.yandex.practicum.order.dto.OrderItemDto;
import ru.yandex.practicum.order.dto.OrderItemRequest;
import ru.yandex.practicum.order.entity.Order;
import ru.yandex.practicum.order.entity.OrderItem;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class OrderMapper {

    public OrderDto toDto(Order order) {
        List<OrderItemDto> itemsDto = order.getItems() == null ? List.of() : order.getItems().stream()
                .map(this::toItemDto)
                .collect(Collectors.toList());
        return new OrderDto(
                order.getId(),
                order.getCustomerName(),
                order.getCustomerEmail(),
                order.getStatus().name(),
                order.getTotalPrice(),
                order.getStatusDetails(),
                order.getCreatedAt(),
                itemsDto
        );
    }

    public OrderItemDto toItemDto(OrderItem item) {
        return new OrderItemDto(
                item.getId(),
                item.getProductId(),
                item.getProductName(),
                item.getQuantity(),
                item.getPrice()
        );
    }

    public OrderItem toOrderItem(OrderItemRequest request, Order order) {
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProductId(request.productId());
        item.setProductName(request.productName());
        item.setQuantity(request.quantity());
        item.setPrice(request.price());
        return item;
    }
}