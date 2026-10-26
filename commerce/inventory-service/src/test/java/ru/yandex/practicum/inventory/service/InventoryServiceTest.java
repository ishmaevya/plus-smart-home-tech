package ru.yandex.practicum.inventory.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.yandex.practicum.inventory.dto.ReleaseRequest;
import ru.yandex.practicum.inventory.dto.ReserveResponse;
import ru.yandex.practicum.inventory.entity.Inventory;
import ru.yandex.practicum.inventory.exception.InsufficientReservationException;
import ru.yandex.practicum.inventory.exception.NotFoundException;
import ru.yandex.practicum.inventory.repository.InventoryRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService(inventoryRepository);
    }

    @Test
    void shouldReleaseReservedStock() {
        Inventory inventory = inventoryWith(10, 4);
        when(inventoryRepository.findByProductId(1L)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReserveResponse response = inventoryService.release(new ReleaseRequest(1L, 3));

        assertThat(response.success()).isTrue();
        assertThat(response.availableQuantity()).isEqualTo(9);
        assertThat(response.message()).isNotBlank();
        assertThat(inventory.getReservedQuantity()).isEqualTo(1);
    }

    @Test
    void shouldRejectReleaseGreaterThanReserved() {
        Inventory inventory = inventoryWith(10, 2);
        when(inventoryRepository.findByProductId(1L)).thenReturn(Optional.of(inventory));

        assertThatThrownBy(() -> inventoryService.release(new ReleaseRequest(1L, 5)))
                .isInstanceOf(InsufficientReservationException.class);

        verify(inventoryRepository, never()).save(any(Inventory.class));
        assertThat(inventory.getReservedQuantity()).isEqualTo(2);
    }

    @Test
    void shouldReturnNotFoundForUnknownProduct() {
        when(inventoryRepository.findByProductId(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.release(new ReleaseRequest(999L, 1)))
                .isInstanceOf(NotFoundException.class);
    }

    private static Inventory inventoryWith(int quantity, int reserved) {
        Inventory inventory = new Inventory();
        inventory.setProductId(1L);
        inventory.setQuantity(quantity);
        inventory.setReservedQuantity(reserved);
        return inventory;
    }
}
