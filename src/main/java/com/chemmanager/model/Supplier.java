package com.chemmanager.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Supplier master data with what it sells us and its recent purchasing history. */
public record Supplier(long id, String code, String name, String contactName, String phone, String email,
                       String address, String wilaya, String paymentTerms, BigDecimal balance, boolean active,
                       long orderCount, LocalDate lastOrderDate, long materialCount, List<Material> materials,
                       List<Order> recentOrders) {

    public Supplier {
        materials = materials == null ? List.of() : List.copyOf(materials);
        recentOrders = recentOrders == null ? List.of() : List.copyOf(recentOrders);
    }

    public record Material(long id, String code, String name, BigDecimal purchasePrice, String unit) {
    }

    public record Order(long id, String orderNumber, LocalDate orderDate, String status, BigDecimal totalTtc) {
    }
}
