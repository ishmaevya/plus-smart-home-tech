package ru.yandex.practicum.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.inventory.dto.InventoryDto;
import ru.yandex.practicum.inventory.dto.ReserveRequest;
import ru.yandex.practicum.inventory.dto.ReserveResponse;
import ru.yandex.practicum.inventory.dto.UpdateInventoryRequest;
import ru.yandex.practicum.inventory.entity.Inventory;
import ru.yandex.practicum.inventory.exception.InsufficientStockException;
import ru.yandex.practicum.inventory.exception.InventoryAlreadyExistsException;
import ru.yandex.practicum.inventory.exception.NotFoundException;
import ru.yandex.practicum.inventory.repository.InventoryRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public List<InventoryDto> findAll() {
        return inventoryRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public InventoryDto getByProductId(Long productId) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new NotFoundException("Складская запись не найдена"));
        return toDto(inventory);
    }

    @Transactional
    public InventoryDto create(UpdateInventoryRequest request) {
        if (inventoryRepository.findByProductId(request.productId()).isPresent()) {
            throw new InventoryAlreadyExistsException(
                    "Запись об остатках для товара " + request.productId() + " уже существует");
        }

        Inventory inventory = new Inventory();
        inventory.setProductId(request.productId());
        inventory.setQuantity(request.quantity());
        inventory.setReservedQuantity(0);
        Inventory saved = inventoryRepository.save(inventory);
        return toDto(saved);
    }

    @Transactional
    public InventoryDto updateQuantity(UpdateInventoryRequest request) {
        Inventory inventory = inventoryRepository.findByProductId(request.productId())
                .orElseThrow(() -> new NotFoundException("Складская запись не найдена"));
        inventory.setQuantity(request.quantity());
        Inventory saved = inventoryRepository.save(inventory);
        return toDto(saved);
    }

    @Transactional
    public ReserveResponse reserve(ReserveRequest request) {
        Inventory inventory = inventoryRepository.findByProductId(request.productId())
                .orElseThrow(() -> new NotFoundException("Складская запись не найдена"));
        int newReserved = (inventory.getReservedQuantity() == null ? 0 : inventory.getReservedQuantity()) + request.quantity();
        int qty = inventory.getQuantity() == null ? 0 : inventory.getQuantity();
        if (newReserved > qty) {
            throw new InsufficientStockException("Недостаточно товара на складе");
        }
        inventory.setReservedQuantity(newReserved);
        Inventory saved = inventoryRepository.save(inventory);
        int available = (saved.getQuantity() == null ? 0 : saved.getQuantity()) - (saved.getReservedQuantity() == null ? 0 : saved.getReservedQuantity());
        return new ReserveResponse(true, available, "Товар успешно зарезервирован");
    }

    private InventoryDto toDto(Inventory inventory) {
        int available = (inventory.getQuantity() == null ? 0 : inventory.getQuantity()) - (inventory.getReservedQuantity() == null ? 0 : inventory.getReservedQuantity());
        return new InventoryDto(
                inventory.getId(),
                inventory.getProductId(),
                inventory.getQuantity(),
                inventory.getReservedQuantity(),
                available
        );
    }
}