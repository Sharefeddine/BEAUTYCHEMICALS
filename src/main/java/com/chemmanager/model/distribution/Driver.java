package com.chemmanager.model.distribution;

/** An employee who can drive a tour; drivers are employees, not a separate register. */
public record Driver(long id, String employeeNumber, String fullName, String position, String phone) {

    @Override
    public String toString() {
        return fullName == null || fullName.isBlank() ? employeeNumber : fullName;
    }
}
