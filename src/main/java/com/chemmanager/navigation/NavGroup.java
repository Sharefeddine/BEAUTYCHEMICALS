package com.chemmanager.navigation;

/** Sidebar sections, mirroring the information architecture of the ERP. */
public enum NavGroup {
    OVERVIEW("nav.group.overview"),
    CATALOG("nav.group.catalog"),
    OPERATIONS("nav.group.operations"),
    COMMERCE("nav.group.commerce"),
    FINANCE("nav.group.finance"),
    ORGANISATION("nav.group.organisation"),
    INSIGHTS("nav.group.insights"),
    SYSTEM("nav.group.system");

    private final String labelKey;

    NavGroup(String labelKey) {
        this.labelKey = labelKey;
    }

    public String labelKey() {
        return labelKey;
    }
}
