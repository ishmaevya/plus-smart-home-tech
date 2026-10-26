package ru.yandex.practicum.inventory.exception;

public class InsufficientReservationException extends RuntimeException {

    public InsufficientReservationException(String message) {
        super(message);
    }
}
