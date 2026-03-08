package com.flippingcopilot.model;

public enum RiskLevel
{
    HIGH,
    MEDIUM,
    LOW;

    public int protoInt()
    {
        switch (this)
        {
            case HIGH:
                return 1;
            case MEDIUM:
                return 2;
            case LOW:
                return 3;
            default:
                return 0;
        }
    }

    public String toApiValue()
    {
        return name().toLowerCase();
    }
}
