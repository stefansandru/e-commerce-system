package com.commerce.inventory.infrastructure;

import com.commerce.inventory.domain.InventoryReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, UUID> {
}
