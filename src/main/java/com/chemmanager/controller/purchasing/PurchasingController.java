package com.chemmanager.controller.purchasing;

import com.chemmanager.controller.ModuleShellController;
import com.chemmanager.navigation.NavigationModule;

/** Sidebar screen for the Purchasing module (delivered in a later phase). */
public final class PurchasingController extends ModuleShellController {

    @Override
    protected NavigationModule module() {
        return NavigationModule.PURCHASING;
    }
}
