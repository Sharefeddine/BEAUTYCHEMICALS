package com.chemmanager.model.safety;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One document of the sheet library.
 *
 * @param isCurrent  the sheet the workshop must read today
 * @param isObsolete the sheet is current but too old to be trusted
 */
public record SafetySheet(long id, long materialId, String materialCode, String materialName,
                          String documentType, String filePath, LocalDate issueDate, String revision,
                          String source, boolean isCurrent, boolean isObsolete, LocalDateTime createdAt) {
}
