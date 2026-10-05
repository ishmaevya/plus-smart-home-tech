package ru.yandex.practicum.inventory.exception;

public class InventoryAlreadyExistsException extends RuntimeException {

    public InventoryAlreadyExistsException(String message) {
        super(message);
    }
}