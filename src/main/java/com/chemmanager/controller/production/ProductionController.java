package com.chemmanager.controller.production;

import com.chemmanager.controller.ModuleShellController;
import com.chemmanager.navigation.NavigationModule;

/** Sidebar screen for the Production module (delivered in a later phase). */
public final class ProductionController extends ModuleShellController {

    @Override
    protected NavigationModule module() {
        return NavigationModule.PRODUCTION;
    }
}
