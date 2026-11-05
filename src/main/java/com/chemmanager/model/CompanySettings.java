package com.chemmanager.model;

import java.math.BigDecimal;

/**
 * Company-wide configuration shown on documents and used for tax defaults.
 * Persisted locally in demo mode and in {@code company_settings} when running against the API.
 */
public record CompanySettings(
        String companyName,
        String legalName,
        String activity,
        String address,
        String city,
        String wilaya,
        String postalCode,
        String phone,
        String email,
        String website,
        String taxId,
        String rc,
        String nif,
        String ai,
        String currency,
        BigDecimal defaultVatRate,
        String invoicePrefix,
        String logoPath,
        String localeCode,
        String theme) {

    public CompanySettings {
        companyName = blankTo(companyName, "ChemManager Demo Company");
        currency = blankTo(currency, "DZD");
        defaultVatRate = defaultVatRate == null ? BigDecimal.valueOf(19) : defaultVatRate;
        invoicePrefix = blankTo(invoicePrefix, "FA");
        localeCode = blankTo(localeCode, "fr");
        theme = blankTo(theme, "LIGHT");
    }

    public static CompanySettings defaults() {
        return new CompanySettings(
                "ChemPro Algérie", "ChemPro Algérie SARL", "Fabrication et distribution de produits chimiques et détergents",
                "Zone industrielle, Lot 42", "Sétif", "Sétif", "19000",
                "+213 36 84 12 34", "contact@chempro.dz", "www.chempro.dz",
                "0012345678", "19/00-1234567B12", "099916001234567", "161234567890",
                "DZD", BigDecimal.valueOf(19), "FA", null, "fr", "LIGHT");
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public String fullAddress() {
        StringBuilder sb = new StringBuilder();
        if (address != null && !address.isBlank()) {
            sb.append(address);
        }
        if (city != null && !city.isBlank()) {
            sb.append(sb.isEmpty() ? "" : ", ").append(city);
        }
        if (wilaya != null && !wilaya.isBlank()) {
            sb.append(sb.isEmpty() ? "" : ", ").append(wilaya);
        }
        if (postalCode != null && !postalCode.isBlank()) {
            sb.append(sb.isEmpty() ? "" : " ").append(postalCode);
        }
        return sb.toString();
    }
}
