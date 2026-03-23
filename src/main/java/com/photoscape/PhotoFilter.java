package com.photoscape;

public enum PhotoFilter
{
    NONE("None"),
    SEPIA("Sepia"),
    BLACK_AND_WHITE("Black & White"),
    VIGNETTE("Vignette"),
    FILM_GRAIN("Film Grain"),
    VINTAGE("Vintage (Sepia + Vignette + Grain)");

    private final String name;

    PhotoFilter(String name)
    {
        this.name = name;
    }

    @Override
    public String toString()
    {
        return name;
    }
}
