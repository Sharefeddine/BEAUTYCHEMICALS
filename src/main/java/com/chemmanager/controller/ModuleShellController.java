package com.chemmanager.controller;

import com.chemmanager.app.AppContext;
import com.chemmanager.component.ModulePlaceholder;
import com.chemmanager.navigation.NavigationModule;

import javafx.fxml.FXML;
import javafx.scene.layout.VBox;

/**
 * Base controller for modules scheduled in a later phase. It renders an explicit, honest
 * placeholder (delivery phase, planned capabilities, working shortcuts) instead of fake
 * buttons, and keeps the FXML/controller pairing required by the architecture.
 */
public abstract class ModuleShellController {

    @FXML
    private VBox placeholderHost;

    /** @return the module this screen belongs to. */
    protected abstract NavigationModule module();

    @FXML
    private void initialize() {
        placeholderHost.getChildren().setAll(
                ModulePlaceholder.create(module(), target -> AppContext.get().navigate(target)));
    }
}
