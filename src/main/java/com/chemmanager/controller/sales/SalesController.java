package com.chemmanager.controller.sales;

import com.chemmanager.controller.ModuleShellController;
import com.chemmanager.navigation.NavigationModule;

/** Sidebar screen for the Sales module (delivered in a later phase). */
public final class SalesController extends ModuleShellController {

    @Override
    protected NavigationModule module() {
        return NavigationModule.SALES;
    }
}
