package com.chemmanager.controller.distribution;

import com.chemmanager.controller.ModuleShellController;
import com.chemmanager.navigation.NavigationModule;

/** Sidebar screen for the Distribution module (delivered in a later phase). */
public final class DistributionController extends ModuleShellController {

    @Override
    protected NavigationModule module() {
        return NavigationModule.DISTRIBUTION;
    }
}
