package com.photoscape;

public enum CompositionGuide
{
    NONE("None"),
    RULE_OF_THIRDS("Rule of Thirds"),
    CENTER_CROSSHAIR("Center Crosshair"),
    ALL("All Guides");

    private final String name;

    CompositionGuide(String name)
    {
        this.name = name;
    }

    @Override
    public String toString()
    {
        return name;
    }
}
