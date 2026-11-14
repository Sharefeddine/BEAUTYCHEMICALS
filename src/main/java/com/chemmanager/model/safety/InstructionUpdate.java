package com.chemmanager.model.safety;

/** The handling instructions an operator saves for a substance. */
public record InstructionUpdate(String ppe, String storage, String handling, String firstAid,
                                String fireMeasures, String spillage) {
}
