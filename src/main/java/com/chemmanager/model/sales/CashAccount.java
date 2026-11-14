package com.chemmanager.model.sales;

/** A cash register or a bank account a payment can be booked on. */
public record CashAccount(long id, String code, String name, String type, String bankName) {

    @Override
    public String toString() {
        return code + " · " + name;
    }
}
