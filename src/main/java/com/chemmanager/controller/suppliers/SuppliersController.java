package com.chemmanager.controller.suppliers;

import com.chemmanager.controller.ModuleShellController;
import com.chemmanager.navigation.NavigationModule;

/** Sidebar screen for the Suppliers module (delivered in a later phase). */
public final class SuppliersController extends ModuleShellController {

    @Override
    protected NavigationModule module() {
        return NavigationModule.SUPPLIERS;
    }
}
