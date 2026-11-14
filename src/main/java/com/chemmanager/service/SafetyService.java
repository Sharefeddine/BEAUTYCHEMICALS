package com.chemmanager.service;

import java.util.List;

import com.chemmanager.dto.PagedResult;
import com.chemmanager.model.safety.HazardPictogram;
import com.chemmanager.model.safety.IncidentSummary;
import com.chemmanager.model.safety.InstructionUpdate;
import com.chemmanager.model.safety.MaterialFile;
import com.chemmanager.model.safety.MaterialHazard;
import com.chemmanager.model.safety.NewIncident;
import com.chemmanager.model.safety.NewSheet;
import com.chemmanager.model.safety.SafetyIncident;
import com.chemmanager.model.safety.SafetyOverview;
import com.chemmanager.model.safety.SafetySheet;

/**
 * The chemical side of the workshop: the register of hazardous substances, the sheet library, the
 * handling instructions and the incident book.
 *
 * <p>The rules live on the server: a sheet older than three years is obsolete whatever its revision,
 * registering a new current sheet retires the previous one, and an incident is investigated before it
 * is closed.</p>
 */
public interface SafetyService {

    /** How much of the register is documented, and what the incident book holds. */
    SafetyOverview overview();

    /** The nine GHS pictograms. */
    List<HazardPictogram> pictograms();

    /**
     * The register, one line per raw material.
     *
     * @param risk {@code hazardous}, {@code missing}, {@code obsolete}, or blank for everything
     */
    PagedResult<MaterialHazard> materials(int page, int pageSize, String search, String risk);

    /** The complete safety file of one substance. */
    MaterialFile material(long id);

    /** The sheet library, newest first. */
    PagedResult<SafetySheet> sheets(int page, int pageSize, String search, String documentType,
                                    boolean currentOnly);

    /** Registers a document; a current sheet retires the previous one of the same type. */
    MaterialFile registerSheet(NewSheet sheet);

    /** Saves the handling instructions of a substance. */
    MaterialFile saveInstructions(long materialId, InstructionUpdate update);

    /** Replaces the pictograms of a substance with the given codes. */
    MaterialFile setPictograms(long materialId, List<String> codes);

    PagedResult<SafetyIncident> incidents(int page, int pageSize, String status, String severity, String search);

    /** The incident counters over the same filter as {@link #incidents}. */
    IncidentSummary incidentSummary(String status, String severity, String search);

    /** Reports an incident; it opens as OPEN and receives its INC number. */
    SafetyIncident reportIncident(NewIncident incident);

    /** Moves an incident to INVESTIGATING or CLOSED. */
    SafetyIncident changeIncidentStatus(long id, String status);
}
