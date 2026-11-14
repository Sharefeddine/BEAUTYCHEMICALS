package com.chemmanager.model.safety;

/**
 * The state of the chemical register in one glance: how many substances are hazardous, how many of
 * them are documented, and what the incident register holds.
 *
 * @param obsoleteSheets current safety data sheets that are older than the validity window
 * @param sheetValidityYears the age after which a sheet is no longer trusted
 */
public record SafetyOverview(int materials, int hazardousMaterials, int materialsWithSheet,
                             int materialsWithoutSheet, int currentSheets, int obsoleteSheets,
                             int materialsWithInstructions, int incidents, int openIncidents,
                             int severeIncidents, int sheetValidityYears) {

    /** Share of the hazardous substances that have a sheet, in percent. */
    public int documentationPct() {
        return hazardousMaterials == 0 ? 100
                : (int) Math.round(materialsWithSheet * 100.0 / hazardousMaterials);
    }
}
