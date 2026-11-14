package com.chemmanager.service.mock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

import com.chemmanager.dto.CustomerQuery;
import com.chemmanager.dto.PagedResult;
import com.chemmanager.model.sales.Customer;
import com.chemmanager.service.CustomerService;

/**
 * Demo customers, used when the backend is not reachable.
 *
 * <p>The dataset mirrors the seeded one: five accounts, one of them with a real receivable and a
 * statement so the screen has something to show, and one cash customer without a credit line.</p>
 */
public final class MockCustomerService implements CustomerService {

    private static final AtomicLong SEQUENCE = new AtomicLong(100);
    private static final List<Customer> CUSTOMERS = new ArrayList<>(List.of(
            customer(1, "CL-0001", "Supérette El Baraka", "COMPANY", "+213 36 44 12 90",
                    "contact@elbaraka.dz", "Cité 1000 logements, bloc B", "Sétif",
                    "150000.00", "0.00", "WHOLESALE", true, 0, 2),
            customer(2, "CL-0002", "Hôtel Atlas", "COMPANY", "+213 36 88 33 21",
                    "achats@hotel-atlas.dz", "Avenue du 8 mai 1945", "Sétif",
                    "300000.00", "42840.00", "SPECIAL", true, 2, 3),
            customer(3, "CL-0003", "Épicerie Amine", "INDIVIDUAL", "+213 550 77 41 18", "",
                    "Rue Larbi Ben M'hidi", "Sétif", "40000.00", "0.00", "RETAIL", true, 0, 1),
            customer(4, "CL-0004", "Grossiste Oued Smar", "COMPANY", "+213 21 55 20 74",
                    "commercial@ouedsmar.dz", "Zone commerciale, lot 41", "Alger",
                    "500000.00", "178500.00", "WHOLESALE", true, 1, 1),
            customer(5, "CL-0005", "Lycée El Hidhab", "COMPANY", "+213 36 60 11 03",
                    "intendance@lycee-hidhab.dz", "Quartier El Hidhab", "Sétif",
                    "0.00", "0.00", "RETAIL", true, 0, 0)));

    @Override
    public PagedResult<Customer> search(CustomerQuery query) {
        CustomerQuery effective = query == null ? CustomerQuery.initial(25) : query;
        String needle = effective.search().toLowerCase(Locale.ROOT);
        String segment = effective.segment();
        List<Customer> matches = CUSTOMERS.stream()
                .filter(customer -> customer.active())
                .filter(customer -> needle.isEmpty()
                        || customer.name().toLowerCase(Locale.ROOT).contains(needle)
                        || customer.code().toLowerCase(Locale.ROOT).contains(needle)
                        || customer.wilaya().toLowerCase(Locale.ROOT).contains(needle))
                .filter(customer -> segment == null || segment.isBlank()
                        || segment.equalsIgnoreCase(customer.priceSegment()))
                .toList();

        int from = Math.min(matches.size(), effective.page() * effective.pageSize());
        int to = Math.min(matches.size(), from + effective.pageSize());

        return new PagedResult<>(matches.subList(from, to), effective.page(), effective.pageSize(), matches.size());
    }

    @Override
    public Customer load(long id) {
        return find(id);
    }

    @Override
    public Customer.Statement statement(long id) {
        Customer customer = find(id);
        List<Customer.Statement.Line> lines = new ArrayList<>();
        BigDecimal running = BigDecimal.ZERO;
        if (customer.balance().signum() > 0) {
            BigDecimal invoice = new BigDecimal("36000.00");
            BigDecimal payment = new BigDecimal("20000.00");
            BigDecimal credit = new BigDecimal("3160.00");
            lines.add(line("INVOICE", 21, "FA-00021", LocalDate.now().minusDays(28), "PARTIAL", invoice,
                    BigDecimal.ZERO, running = running.add(invoice)));
            lines.add(line("PAYMENT", 8, "ENC-00008", LocalDate.now().minusDays(15), "TRANSFER",
                    BigDecimal.ZERO, payment, running = running.subtract(payment)));
            lines.add(line("INVOICE", 27, "FA-00027", LocalDate.now().minusDays(8), "PARTIAL",
                    new BigDecimal("17850.00"), BigDecimal.ZERO, running = running.add(new BigDecimal("17850.00"))));
            lines.add(line("CREDIT", 3, "AV-00003", LocalDate.now().minusDays(2), "APPLIED", BigDecimal.ZERO, credit,
                    running = running.subtract(credit)));
        }

        return new Customer.Statement(customer, null, null, lines,
                lines.stream().map(Customer.Statement.Line::debit).reduce(BigDecimal.ZERO, BigDecimal::add),
                lines.stream().map(Customer.Statement.Line::credit).reduce(BigDecimal.ZERO, BigDecimal::add),
                customer.balance());
    }

    @Override
    public List<Customer> selectable() {
        return CUSTOMERS.stream().filter(Customer::active).toList();
    }

    @Override
    public Customer create(Customer.NewCustomer request) {
        long id = SEQUENCE.incrementAndGet();
        BigDecimal limit = request.creditLimit() == null ? BigDecimal.ZERO : request.creditLimit();
        Customer created = new Customer(id, String.format("CL-%04d", id % 10000),
                request.name() == null ? "" : request.name(),
                request.type() == null ? "COMPANY" : request.type(),
                request.phone() == null ? "" : request.phone(),
                request.email() == null ? "" : request.email(),
                request.address() == null ? "" : request.address(),
                request.wilaya() == null ? "" : request.wilaya(),
                limit, BigDecimal.ZERO, limit.signum() > 0 ? limit : null,
                request.priceSegment() == null ? "RETAIL" : request.priceSegment(),
                request.notes() == null ? "" : request.notes(), true, 0, 0, BigDecimal.ZERO);
        CUSTOMERS.add(created);

        return created;
    }

    @Override
    public Customer update(long id, Customer.NewCustomer request) {
        Customer current = find(id);
        BigDecimal limit = request.creditLimit() == null ? current.creditLimit() : request.creditLimit();
        Customer updated = new Customer(current.id(), current.code(),
                request.name() == null ? current.name() : request.name(),
                request.type() == null ? current.type() : request.type(),
                request.phone() == null ? current.phone() : request.phone(),
                request.email() == null ? current.email() : request.email(),
                request.address() == null ? current.address() : request.address(),
                request.wilaya() == null ? current.wilaya() : request.wilaya(),
                limit, current.balance(), limit.subtract(current.balance()),
                request.priceSegment() == null ? current.priceSegment() : request.priceSegment(),
                request.notes() == null ? current.notes() : request.notes(),
                request.active() == null ? current.active() : request.active(),
                current.invoiceCount(), current.orderCount(), current.outstanding());
        CUSTOMERS.replaceAll(candidate -> candidate.id() == id ? updated : candidate);

        return updated;
    }

    @Override
    public Customer deactivate(long id) {
        Customer current = find(id);
        Customer closed = new Customer(current.id(), current.code(), current.name(), current.type(),
                current.phone(), current.email(), current.address(), current.wilaya(), current.creditLimit(),
                current.balance(), current.availableCredit(), current.priceSegment(), current.notes(), false,
                current.invoiceCount(), current.orderCount(), current.outstanding());
        CUSTOMERS.replaceAll(candidate -> candidate.id() == id ? closed : candidate);

        return closed;
    }

    private static Customer find(long id) {
        return CUSTOMERS.stream().filter(customer -> customer.id() == id).findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown customer " + id));
    }

    private static Customer.Statement.Line line(String kind, long id, String number, LocalDate date, String status,
                                                BigDecimal debit, BigDecimal credit, BigDecimal balance) {
        return new Customer.Statement.Line(kind, id, number, date, status, debit, credit, balance);
    }

    private static Customer customer(long id, String code, String name, String type, String phone, String email,
                                     String address, String wilaya, String creditLimit, String balance,
                                     String segment, boolean active, int invoices, int orders) {
        BigDecimal limit = new BigDecimal(creditLimit);
        BigDecimal owed = new BigDecimal(balance);

        return new Customer(id, code, name, type, phone, email, address, wilaya, limit, owed,
                limit.signum() > 0 ? limit.subtract(owed) : null, segment, "", active, invoices, orders, owed);
    }
}
