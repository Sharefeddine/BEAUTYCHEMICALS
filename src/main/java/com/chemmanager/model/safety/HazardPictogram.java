package com.chemmanager.model.safety;

/** One GHS pictogram of the chemical register. */
public record HazardPictogram(long id, String code, String nameFr, String nameAr, String nameEn,
                              String description, int materialCount) {
}
