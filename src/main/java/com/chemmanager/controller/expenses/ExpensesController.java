package com.chemmanager.controller.expenses;

import com.chemmanager.controller.ModuleShellController;
import com.chemmanager.navigation.NavigationModule;

/** Sidebar screen for the Expenses module (delivered in a later phase). */
public final class ExpensesController extends ModuleShellController {

    @Override
    protected NavigationModule module() {
        return NavigationModule.EXPENSES;
    }
}
