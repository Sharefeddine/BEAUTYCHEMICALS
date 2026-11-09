package com.chemmanager.service;

import java.math.BigDecimal;
import java.util.List;

import com.chemmanager.dto.FormulationComparison;
import com.chemmanager.dto.FormulationQuery;
import com.chemmanager.dto.PagedResult;
import com.chemmanager.model.Formulation;
import com.chemmanager.model.FormulationItem;
import com.chemmanager.model.FormulationVersion;

/**
 * Formulations: read, create, version, edit a draft, approve.
 *
 * <p>The interface deliberately has no {@code generate} method: the bill of materials is always
 * entered by a qualified user and the application only stores, costs and freezes it.</p>
 */
public interface FormulationService {

    PagedResult<Formulation> search(FormulationQuery query);

    /** The formulation with its version history (the current version carries its items). */
    Formulation load(long id);

    List<FormulationVersion> versions(long formulationId);

    FormulationVersion version(long formulationId, long versionId);

    Formulation create(String nameFr, String nameAr, Long productId, String notes);

    /** Creates the next version, copying the newest one so the change is a diff. */
    FormulationVersion createVersion(long formulationId);

    /** Replaces the content of a draft version (refused with 409 once approved). */
    FormulationVersion updateVersion(long formulationId, long versionId, String instructions,
                                     BigDecimal expectedYield, String yieldUnit, List<FormulationItem> items);

    FormulationVersion approve(long formulationId, long versionId);

    Formulation archive(long formulationId);

    FormulationComparison compare(long formulationId, long leftVersionId, long rightVersionId);
}
