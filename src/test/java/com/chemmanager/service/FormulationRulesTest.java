package com.chemmanager.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.chemmanager.dto.FormulationComparison;
import com.chemmanager.dto.FormulationQuery;
import com.chemmanager.model.Formulation;
import com.chemmanager.model.FormulationItem;
import com.chemmanager.model.FormulationVersion;
import com.chemmanager.service.mock.MockFormulationService;

/**
 * The formulation rules that must never regress: nothing is generated, an approved version is
 * frozen, a change goes through a new version and the cost always follows the current prices.
 */
class FormulationRulesTest {

    private final MockFormulationService service = new MockFormulationService();

    @Test
    @DisplayName("the demo dataset ships formula families with an approved version")
    void demoDatasetIsPopulated() {
        var page = service.search(FormulationQuery.initial(50));
        assertFalse(page.items().isEmpty(), "the demo dataset must contain formulations");
        assertTrue(page.items().stream().anyMatch(Formulation::approved),
                "at least one formulation must be approved so the lock can be exercised");
    }

    @Test
    @DisplayName("a new version copies the current content and starts unlocked")
    void newVersionCopiesContent() {
        Formulation approved = service.search(FormulationQuery.initial(50)).items().stream()
                .filter(Formulation::approved).findFirst().orElseThrow();
        List<FormulationVersion> before = service.versions(approved.id());

        FormulationVersion created = service.createVersion(approved.id());

        assertFalse(created.locked());
        assertEquals(before.size() + 1, service.versions(approved.id()).size());
        assertEquals(before.get(0).items().size(), created.items().size(),
                "the new version starts from the previous bill of materials");
        assertTrue(created.version() > before.get(0).version());
    }

    @Test
    @DisplayName("an approved version cannot be edited")
    void approvedVersionIsFrozen() {
        Formulation approved = service.search(FormulationQuery.initial(50)).items().stream()
                .filter(Formulation::approved).findFirst().orElseThrow();
        FormulationVersion locked = service.versions(approved.id()).get(0);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.updateVersion(approved.id(), locked.id(), "tentative", BigDecimal.ONE, "KG",
                        List.of()));

        assertEquals("FORMULATION_LOCKED", error.getMessage());
        assertTrue(service.versions(approved.id()).get(0).locked());
    }

    @Test
    @DisplayName("an empty version cannot be approved")
    void emptyVersionIsRefused() {
        Formulation created = service.create("Formule vide", "تركيبة فارغة", null, "");
        FormulationVersion version = service.createVersion(created.id());

        assertThrows(IllegalArgumentException.class, () -> service.approve(created.id(), version.id()));
    }

    @Test
    @DisplayName("editing a draft prices the lines and computes the percentages")
    void draftIsCostedFromCurrentPrices() {
        Formulation created = service.create("Gel test", "جل تجريبي", null, "");
        FormulationVersion version = service.createVersion(created.id());

        FormulationVersion updated = service.updateVersion(created.id(), version.id(),
                "Mélanger jusqu'à homogénéité.", BigDecimal.valueOf(10), "KG",
                List.of(new FormulationItem(1, "M-001", "Tensioactif", new BigDecimal("4.000"),
                                "KG", BigDecimal.ZERO, "A", BigDecimal.ZERO, BigDecimal.ZERO, ""),
                        new FormulationItem(2, "M-002", "Parfum", new BigDecimal("6.000"),
                                "KG", BigDecimal.ZERO, "B", BigDecimal.ZERO, BigDecimal.ZERO, "")));

        assertEquals(2, updated.itemCount());
        assertTrue(updated.cost().signum() > 0, "the cost must be computed from the purchase prices");
        assertEquals(0, new BigDecimal("100.0000").compareTo(updated.items().get(0).percentage()
                .add(updated.items().get(1).percentage())), "the percentages must add up to 100");
    }

    @Test
    @DisplayName("approving locks the version, promotes it and stamps the approver")
    void approvalLocksAndPromotes() {
        Formulation created = service.create("Nettoyant sol", "منظف أرضيات", null, "");
        FormulationVersion version = service.createVersion(created.id());
        service.updateVersion(created.id(), version.id(), "", BigDecimal.valueOf(5), "L",
                List.of(new FormulationItem(1, "M-001", "Tensioactif", new BigDecimal("5.000"),
                        "L", BigDecimal.ZERO, "A", BigDecimal.ZERO, BigDecimal.ZERO, "")));

        FormulationVersion approved = service.approve(created.id(), version.id());
        Formulation reloaded = service.load(created.id());

        assertTrue(approved.locked());
        assertNotNull(approved.approvedAt());
        assertEquals("APPROVED", reloaded.status());
        assertEquals(version.id(), reloaded.currentVersionId());
    }

    @Test
    @DisplayName("comparing two versions reports the changed lines and the cost delta")
    void comparisonReportsTheDelta() {
        Formulation approved = service.search(FormulationQuery.initial(50)).items().stream()
                .filter(Formulation::approved).findFirst().orElseThrow();
        FormulationVersion first = service.versions(approved.id()).get(0);
        FormulationVersion second = service.createVersion(approved.id());
        List<FormulationItem> items = second.items();
        FormulationItem head = items.get(0);
        List<FormulationItem> changed = new java.util.ArrayList<>(items);
        changed.set(0, head.withQuantity(head.quantity().add(BigDecimal.TEN)));
        service.updateVersion(approved.id(), second.id(), second.instructions(), second.expectedYield(),
                second.yieldUnit(), changed);

        FormulationComparison comparison = service.compare(approved.id(), first.id(), second.id());

        assertTrue(comparison.lines().stream().anyMatch(FormulationComparison.Line::changed),
                "the edited line must be reported as changed");
        assertTrue(comparison.costDelta().signum() > 0, "adding material must raise the cost");
        assertEquals(first.version(), comparison.leftVersion());
        assertEquals(second.version(), comparison.rightVersion());
    }

    @Test
    @DisplayName("archiving keeps the versions readable")
    void archivingKeepsHistory() {
        Formulation approved = service.search(FormulationQuery.initial(50)).items().stream()
                .filter(Formulation::approved).findFirst().orElseThrow();
        Formulation archived = service.archive(approved.id());

        assertEquals("ARCHIVED", archived.status());
        assertFalse(service.versions(approved.id()).isEmpty(), "the history must survive the archive");
    }
}
