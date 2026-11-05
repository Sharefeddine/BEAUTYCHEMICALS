package com.chemmanager.controller.formulations;

import com.chemmanager.controller.ModuleShellController;
import com.chemmanager.navigation.NavigationModule;

/** Sidebar screen for the Formulations module (delivered in a later phase). */
public final class FormulationsController extends ModuleShellController {

    @Override
    protected NavigationModule module() {
        return NavigationModule.FORMULATIONS;
    }
}
