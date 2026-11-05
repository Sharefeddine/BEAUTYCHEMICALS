package com.chemmanager.controller.customers;

import com.chemmanager.controller.ModuleShellController;
import com.chemmanager.navigation.NavigationModule;

/** Sidebar screen for the Customers module (delivered in a later phase). */
public final class CustomersController extends ModuleShellController {

    @Override
    protected NavigationModule module() {
        return NavigationModule.CUSTOMERS;
    }
}
