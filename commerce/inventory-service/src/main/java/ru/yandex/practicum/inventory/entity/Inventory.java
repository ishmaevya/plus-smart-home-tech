package ru.yandex.practicum.inventory.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(unique = true, nullable = false)
    private Long productId;

    @Min(0)
    private Integer quantity = 0;

    @Min(0)
    private Integer reservedQuantity = 0;

    @Version
    private Long version;

    private Integer availableQuantity;

    @PostLoad
    @PostPersist
    @PostUpdate
    public void recalculateAvailable() {
        if (quantity == null) quantity = 0;
        if (reservedQuantity == null) reservedQuantity = 0;
        this.availableQuantity = quantity - reservedQuantity;
    }
}