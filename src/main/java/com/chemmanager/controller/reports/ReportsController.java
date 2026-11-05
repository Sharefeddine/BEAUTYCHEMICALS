package com.chemmanager.controller.reports;

import com.chemmanager.controller.ModuleShellController;
import com.chemmanager.navigation.NavigationModule;

/** Sidebar screen for the Reports module (delivered in a later phase). */
public final class ReportsController extends ModuleShellController {

    @Override
    protected NavigationModule module() {
        return NavigationModule.REPORTS;
    }
}
