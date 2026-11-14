package com.chemmanager.model.safety;

import java.time.LocalDate;

/** A document registered in the sheet library. */
public record NewSheet(long materialId, String documentType, String filePath, LocalDate issueDate,
                       String revision, String source) {
}
