package com.chemmanager.model.finance;

import java.time.LocalDate;

/** One day of the attendance sheet: the unique key is the employee and the day. */
public record AttendanceRecord(long id, LocalDate workDate, String status, String checkIn, String checkOut,
                               String notes) {
}
