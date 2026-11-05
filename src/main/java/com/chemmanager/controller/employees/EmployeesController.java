package com.chemmanager.controller.employees;

import com.chemmanager.controller.ModuleShellController;
import com.chemmanager.navigation.NavigationModule;

/** Sidebar screen for the Employees module (delivered in a later phase). */
public final class EmployeesController extends ModuleShellController {

    @Override
    protected NavigationModule module() {
        return NavigationModule.EMPLOYEES;
    }
}
