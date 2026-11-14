package com.chemmanager.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
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
import com.chemmanager.service.mock.MockSafetyService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The rules of the chemical register, replayed against the offline service.
 *
 * <p>The same four rules the server enforces are checked here: a sheet older than three years is
 * obsolete, a new current sheet retires the previous one, the pictograms are a set, and an incident
 * is investigated before it is closed and never reopened afterwards.</p>
 */
class SafetyServiceMockTest {

    private MockSafetyService safety;

    @BeforeEach
    void setUp() {
        safety = new MockSafetyService();
    }

    @Test
    @DisplayName("the register counts its substances, its sheets and its incidents")
    void overviewCountsTheRegister() {
        SafetyOverview overview = safety.overview();

        assertEquals(12, overview.materials(), "the demo register holds twelve materials");
        assertEquals(9, overview.hazardousMaterials(), "nine of them are classified");
        assertEquals(3, overview.materialsWithSheet(), "three have a safety data sheet");
        assertEquals(6, overview.materialsWithoutSheet(), "the hazardous ones without a sheet are counted");
        assertEquals(3, overview.currentSheets());
        assertEquals(1, overview.obsoleteSheets(), "the hydrochloric acid sheet is expired");
        assertEquals(2, overview.materialsWithInstructions());
        assertEquals(2, overview.incidents());
        assertEquals(1, overview.openIncidents());
        assertEquals(0, overview.severeIncidents());
        assertEquals(3, overview.sheetValidityYears());
        assertEquals(33, overview.documentationPct(), "three sheets over nine hazardous substances");
    }

    @Test
    @DisplayName("the nine GHS pictograms are served with their material count")
    void pictogramsAreTheNine() {
        List<HazardPictogram> pictograms = safety.pictograms();

        assertEquals(9, pictograms.size());
        assertTrue(pictograms.stream().allMatch(pictogram -> pictogram.code().startsWith("GHS")));
        HazardPictogram corrosive = pictograms.stream().filter(pictogram -> "GHS05".equals(pictogram.code()))
                .findFirst().orElseThrow();
        assertTrue(corrosive.materialCount() >= 3, "the corrosive pictogram documents several substances");
    }

    @Test
    @DisplayName("the register filters on risk: hazardous, missing and obsolete")
    void registerFiltersOnRisk() {
        PagedResult<MaterialHazard> hazardous = safety.materials(1, 25, "", "hazardous");
        assertEquals(9, hazardous.total());
        assertTrue(hazardous.items().stream().allMatch(MaterialHazard::isHazardous));

        PagedResult<MaterialHazard> missing = safety.materials(1, 25, "", "missing");
        assertEquals(6, missing.total());
        assertTrue(missing.items().stream().allMatch(MaterialHazard::isUndocumented));

        PagedResult<MaterialHazard> obsolete = safety.materials(1, 25, "", "obsolete");
        assertEquals(1, obsolete.total());
        assertEquals("MP-0003", obsolete.items().get(0).code());

        PagedResult<MaterialHazard> all = safety.materials(1, 25, "", "");
        assertEquals(12, all.total());
        assertTrue(all.items().get(0).isHazardous(), "the hazardous substances come first");
    }

    @Test
    @DisplayName("the register searches on the code, the names and the hazard class")
    void registerSearches() {
        assertEquals(1, safety.materials(1, 25, "MP-0002", "").total());
        assertEquals(1, safety.materials(1, 25, "soude caustique liquide", "").total());
        assertTrue(safety.materials(1, 25, "GHS05", "").total() >= 3);
        assertEquals(0, safety.materials(1, 25, "introuvable-xyz", "").total());
    }

    @Test
    @DisplayName("the register pages its substances")
    void registerPages() {
        PagedResult<MaterialHazard> first = safety.materials(1, 5, "", "");
        PagedResult<MaterialHazard> last = safety.materials(3, 5, "", "");

        assertEquals(5, first.items().size());
        assertEquals(2, last.items().size());
        assertEquals(12, first.total());
        assertEquals(3, first.totalPages());
        assertFalse(first.items().get(0).code().equals(last.items().get(0).code()));
    }

    @Test
    @DisplayName("the file of a substance holds its sheets, its instructions and its history")
    void materialFileIsComplete() {
        MaterialFile file = safety.material(2);

        assertEquals("MP-0002", file.material().code());
        assertTrue(file.material().hasSheet());
        assertFalse(file.material().sheetObsolete());
        assertEquals("REV-2", file.material().sheetRevision());
        assertNotNull(file.instructions(), "the caustic soda has handling instructions");
        assertTrue(file.instructions().ppe().contains("visière"));
        assertEquals(List.of("GHS05"),
                file.pictograms().stream().map(HazardPictogram::code).toList());
        assertEquals(1, file.incidents().size(), "the spill of INC-00001 concerns this substance");
        assertEquals("INC-00001", file.incidents().get(0).incidentNumber());
    }

    @Test
    @DisplayName("a substance without a sheet reads as missing and keeps an empty history")
    void undocumentedMaterialReadsAsMissing() {
        MaterialFile file = safety.material(7);

        assertTrue(file.material().isUndocumented());
        assertNull(file.material().sheetId());
        assertFalse(file.material().hasSheet());
        assertTrue(file.sheets().isEmpty());
        assertNull(file.instructions());
        assertTrue(file.incidents().isEmpty());
    }

    @Test
    @DisplayName("a new current sheet retires the previous one of the same type")
    void registeringASheetRetiresThePrevious() {
        MaterialFile before = safety.material(1);
        long previousCount = before.sheets().stream().filter(SafetySheet::isCurrent)
                .filter(sheet -> "SDS".equals(sheet.documentType())).count();
        assertEquals(1, previousCount);

        MaterialFile after = safety.registerSheet(new NewSheet(1, "SDS", "sds/MP-0001-REV-5.pdf",
                LocalDate.now(), "REV-5", "Chimie Sétif"));

        assertEquals("REV-5", after.material().sheetRevision());
        assertFalse(after.material().sheetObsolete());
        assertEquals(3, after.sheets().size(), "the two older revisions are kept");
        assertEquals(1, after.sheets().stream().filter(SafetySheet::isCurrent)
                .filter(sheet -> "SDS".equals(sheet.documentType())).count(),
                "only one sheet of a type may be current");
        assertEquals(3, safety.overview().materialsWithSheet(),
                "MP-0001 already had a sheet: the count does not move");
    }

    @Test
    @DisplayName("a sheet issued more than three years ago is obsolete on arrival")
    void oldSheetIsObsoleteOnArrival() {
        MaterialFile after = safety.registerSheet(new NewSheet(7, "SDS", "sds/MP-0007-REV-1.pdf",
                LocalDate.now().minusYears(4), "REV-1", "Chimie Sétif"));

        assertTrue(after.material().hasSheet(), "the substance is documented now");
        assertTrue(after.material().sheetObsolete(), "but the sheet is too old to be trusted");
        assertEquals(2, safety.overview().obsoleteSheets());
    }

    @Test
    @DisplayName("a sheet without a file or of an unknown type is refused")
    void registeringAnInvalidSheetIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> safety.registerSheet(new NewSheet(1, "SDS", "  ", LocalDate.now(), "REV-6", "")));
        assertThrows(IllegalArgumentException.class,
                () -> safety.registerSheet(new NewSheet(1, "UNKNOWN", "sds/x.pdf", LocalDate.now(), "R", "")));
        assertThrows(IllegalArgumentException.class,
                () -> safety.registerSheet(new NewSheet(999, "SDS", "sds/x.pdf", LocalDate.now(), "R", "")));
    }

    @Test
    @DisplayName("the sheet library filters on the document type and on the current revision")
    void sheetLibraryFilters() {
        assertEquals(4, safety.sheets(1, 25, "", "", false).total());
        assertEquals(3, safety.sheets(1, 25, "", "SDS", true).total());
        assertEquals(0, safety.sheets(1, 25, "", "TRANSPORT", false).total());
        assertEquals(2, safety.sheets(1, 25, "MP-0001", "", false).total(),
                "the two revisions of the soap base are listed");
    }

    @Test
    @DisplayName("the handling instructions are saved as one block per substance")
    void instructionsAreSaved() {
        MaterialFile saved = safety.saveInstructions(7, new InstructionUpdate("Gants, lunettes",
                "Sac fermé", "Éviter la poussière", "Rincer", "Poudre ABC", "Balayer"));

        assertNotNull(saved.instructions());
        assertEquals("Sac fermé", saved.instructions().storage());
        assertEquals(3, safety.overview().materialsWithInstructions());

        MaterialFile updated = safety.saveInstructions(7, new InstructionUpdate("Gants",
                "Sac fermé et palettisé", "Éviter la poussière", "Rincer", "Poudre ABC", "Balayer"));
        assertEquals("Sac fermé et palettisé", updated.instructions().storage());
        assertEquals(3, safety.overview().materialsWithInstructions(),
                "saving twice does not create a second block");
    }

    @Test
    @DisplayName("the pictograms of a substance are replaced as a set")
    void pictogramsAreASet() {
        MaterialFile updated = safety.setPictograms(7, List.of("GHS05", "GHS07"));

        assertEquals(List.of("GHS05", "GHS07"),
                updated.pictograms().stream().map(HazardPictogram::code).toList());
        assertEquals("GHS05,GHS07", updated.material().hazardPictograms());

        MaterialFile cleared = safety.setPictograms(7, List.of());
        assertTrue(cleared.pictograms().isEmpty());
        assertEquals("", cleared.material().hazardPictograms());

        assertThrows(IllegalArgumentException.class, () -> safety.setPictograms(7, List.of("GHS99")));
    }

    @Test
    @DisplayName("the incident register filters on the status and on the severity")
    void incidentRegisterFilters() {
        assertEquals(2, safety.incidents(1, 25, "", "", "").total());
        assertEquals(1, safety.incidents(1, 25, "OPEN", "", "").total());
        assertEquals(1, safety.incidents(1, 25, "CLOSED", "", "").total());
        assertEquals(1, safety.incidents(1, 25, "", "MODERATE", "").total());
        assertEquals(0, safety.incidents(1, 25, "", "CRITICAL", "").total());
        assertEquals(1, safety.incidents(1, 25, "", "", "projection").total());
    }

    @Test
    @DisplayName("the incident summary follows the same filter as the list")
    void incidentSummaryFollowsTheFilter() {
        IncidentSummary all = safety.incidentSummary("", "", "");
        assertEquals(2, all.total());
        assertEquals(1, all.open());
        assertEquals(0, all.severe());

        IncidentSummary open = safety.incidentSummary("OPEN", "", "");
        assertEquals(1, open.total());
        assertEquals(1, open.open());
    }

    @Test
    @DisplayName("a reported incident opens with its INC number")
    void incidentIsReportedWithItsNumber() {
        SafetyIncident reported = safety.reportIncident(new NewIncident(2L, "SPILL", "MAJOR",
                "Débordement de cuve", "Zone évacuée"));

        assertEquals("INC-00003", reported.incidentNumber());
        assertEquals("OPEN", reported.status());
        assertNull(reported.closedAt());
        assertEquals("Soude caustique liquide 30%", reported.materialName());
        assertEquals(3, safety.overview().incidents());
        assertEquals(2, safety.overview().openIncidents());
        assertEquals(1, safety.overview().severeIncidents());
    }

    @Test
    @DisplayName("an incident without a description or with an unknown type is refused")
    void invalidIncidentIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> safety.reportIncident(new NewIncident(null, "SPILL", "MINOR", " ", "")));
        assertThrows(IllegalArgumentException.class,
                () -> safety.reportIncident(new NewIncident(null, "TSUNAMI", "MINOR", "Texte", "")));
        assertThrows(IllegalArgumentException.class,
                () -> safety.reportIncident(new NewIncident(null, "SPILL", "APOCALYPTIC", "Texte", "")));
    }

    @Test
    @DisplayName("an incident is investigated before it is closed, and never reopened")
    void incidentWalksOpenToInvestigatingToClosed() {
        assertThrows(IllegalStateException.class,
                () -> safety.changeIncidentStatus(2, "CLOSED"),
                "an open incident cannot jump to closed");

        SafetyIncident investigating = safety.changeIncidentStatus(2, "INVESTIGATING");
        assertEquals("INVESTIGATING", investigating.status());
        assertNull(investigating.closedAt());

        SafetyIncident closed = safety.changeIncidentStatus(2, "CLOSED");
        assertEquals("CLOSED", closed.status());
        assertNotNull(closed.closedAt());
        assertEquals(0, safety.overview().openIncidents());

        assertThrows(IllegalStateException.class, () -> safety.changeIncidentStatus(2, "OPEN"),
                "a closed incident is terminal");
        assertThrows(IllegalStateException.class, () -> safety.changeIncidentStatus(2, "INVESTIGATING"));
    }

    @Test
    @DisplayName("an unknown incident or status is refused")
    void unknownIncidentStatusIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> safety.changeIncidentStatus(999, "CLOSED"));
        assertThrows(IllegalArgumentException.class, () -> safety.changeIncidentStatus(2, "PENDING"));
        assertThrows(IllegalArgumentException.class, () -> safety.changeIncidentStatus(2, ""));
    }

    @Test
    @DisplayName("the register and the incident book survive a reload of the overview")
    void overviewIsStable() {
        SafetyOverview first = safety.overview();
        safety.material(1);
        SafetyOverview second = safety.overview();

        assertEquals(first.materials(), second.materials());
        assertEquals(first.hazardousMaterials(), second.hazardousMaterials());
        assertEquals(first.currentSheets(), second.currentSheets());
        assertEquals(first.obsoleteSheets(), second.obsoleteSheets());
    }
}
