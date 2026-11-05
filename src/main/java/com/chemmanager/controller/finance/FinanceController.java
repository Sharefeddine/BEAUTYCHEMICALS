package com.chemmanager.controller.finance;

import com.chemmanager.controller.ModuleShellController;
import com.chemmanager.navigation.NavigationModule;

/** Sidebar screen for the Finance module (delivered in a later phase). */
public final class FinanceController extends ModuleShellController {

    @Override
    protected NavigationModule module() {
        return NavigationModule.FINANCE;
    }
}
