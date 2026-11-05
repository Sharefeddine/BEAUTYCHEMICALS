package com.chemmanager.controller.safety;

import com.chemmanager.controller.ModuleShellController;
import com.chemmanager.navigation.NavigationModule;

/** Sidebar screen for the Safety module (delivered in a later phase). */
public final class SafetyController extends ModuleShellController {

    @Override
    protected NavigationModule module() {
        return NavigationModule.SAFETY;
    }
}
