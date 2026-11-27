package com.houssen.liberoshop.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ChargeBasis {
    @JsonProperty("lot")  LOT,
    @JsonProperty("unit") UNIT
}
