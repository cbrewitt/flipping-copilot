package com.flippingcopilot.model;

public enum RiskLevel
{
    LOW,
    MEDIUM,
    HIGH;

    public String toApiValue()
    {
        return name().toLowerCase();
    }
}
