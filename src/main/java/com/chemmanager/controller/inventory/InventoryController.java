package com.chemmanager.controller.inventory;

import com.chemmanager.controller.ModuleShellController;
import com.chemmanager.navigation.NavigationModule;

/** Sidebar screen for the Inventory module (delivered in a later phase). */
public final class InventoryController extends ModuleShellController {

    @Override
    protected NavigationModule module() {
        return NavigationModule.INVENTORY;
    }
}
