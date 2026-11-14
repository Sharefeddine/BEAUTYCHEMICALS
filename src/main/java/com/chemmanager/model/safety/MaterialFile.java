package com.chemmanager.model.safety;

import java.util.List;

/** The complete safety file of one material: the register line, its sheets, instructions and history. */
public record MaterialFile(MaterialHazard material, List<SafetySheet> sheets, SafetyInstructions instructions,
                           List<HazardPictogram> pictograms, List<SafetyIncident> incidents) {
}
