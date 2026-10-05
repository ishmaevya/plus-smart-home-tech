package ru.yandex.practicum.inventory.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.inventory.dto.InventoryDto;
import ru.yandex.practicum.inventory.dto.ReserveRequest;
import ru.yandex.practicum.inventory.dto.ReserveResponse;
import ru.yandex.practicum.inventory.dto.UpdateInventoryRequest;
import ru.yandex.practicum.inventory.service.InventoryService;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public List<InventoryDto> getAllInventory() {
        return inventoryService.findAll();
    }

    @GetMapping("/{productId}")
    public InventoryDto getByProductId(@PathVariable Long productId) {
        return inventoryService.getByProductId(productId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryDto createInventory(@RequestBody @Valid UpdateInventoryRequest request) {
        return inventoryService.create(request);
    }

    @PutMapping
    public InventoryDto updateInventory(@RequestBody @Valid UpdateInventoryRequest request) {
        return inventoryService.updateQuantity(request);
    }

    @PostMapping("/reserve")
    public ReserveResponse reserveStock(@RequestBody @Valid ReserveRequest request) {
        return inventoryService.reserve(request);
    }
}
