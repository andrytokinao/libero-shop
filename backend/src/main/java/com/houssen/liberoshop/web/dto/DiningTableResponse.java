package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.DiningTable;

/** A table and the token its QR code carries -- for the administrator printing the codes. */
public record DiningTableResponse(Long id, String name, String token) {

    public static DiningTableResponse of(DiningTable table) {
        return new DiningTableResponse(table.getId(), table.getName(), table.getToken());
    }
}
