package com.chemmanager.service;

import java.util.List;

import com.chemmanager.dto.CustomerQuery;
import com.chemmanager.dto.PagedResult;
import com.chemmanager.model.sales.Customer;

/**
 * The customer file, its credit line and its statement of account.
 *
 * <p>The client never decides what a customer may buy: the credit guard lives on the server, and
 * this service only reads the limit and the balance it reports.</p>
 */
public interface CustomerService {

    PagedResult<Customer> search(CustomerQuery query);

    Customer load(long id);

    Customer.Statement statement(long id);

    List<Customer> selectable();

    Customer create(Customer.NewCustomer request);

    Customer update(long id, Customer.NewCustomer request);

    Customer deactivate(long id);
}
