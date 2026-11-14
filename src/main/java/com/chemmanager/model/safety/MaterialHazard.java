package com.chemmanager.model.safety;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/** One line of the chemical register: a raw material and the safety paperwork behind it. */
public record MaterialHazard(long id, String code, String commercialName, String chemicalName,
                             String hazardClass, String hazardPictograms, String storageConditions,
                             String storageLocation, boolean isHazardous, boolean hasSheet, Long sheetId,
                             LocalDate sheetIssueDate, String sheetRevision, boolean sheetObsolete,
                             int instructionCount, int pictogramCount, int incidentCount) {

    /** The pictogram codes of the material, as the register stores them. */
    public List<String> pictogramCodes() {
        if (hazardPictograms == null || hazardPictograms.isBlank()) {
            return List.of();
        }

        return Arrays.stream(hazardPictograms.split(","))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .toList();
    }

    /** True when a hazardous substance has no sheet on file. */
    public boolean isUndocumented() {
        return isHazardous && !hasSheet;
    }
}
