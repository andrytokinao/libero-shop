package com.houssen.liberoshop.service.delivery.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Base de calcul d'une charge : montant global du lot, ou montant par unité reçue. */
public enum ChargeBasis {
    @JsonProperty("lot")  LOT,
    @JsonProperty("unit") UNIT
}
