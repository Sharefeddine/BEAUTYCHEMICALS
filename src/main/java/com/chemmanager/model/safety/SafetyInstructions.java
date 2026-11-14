package com.chemmanager.model.safety;

/** The handling instructions posted next to a substance: PPE, storage, first aid, spillage. */
public record SafetyInstructions(Long id, long materialId, String ppe, String storage, String handling,
                                 String firstAid, String fireMeasures, String spillage) {

    public boolean isEmpty() {
        return blank(ppe) && blank(storage) && blank(handling) && blank(firstAid) && blank(fireMeasures)
                && blank(spillage);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
