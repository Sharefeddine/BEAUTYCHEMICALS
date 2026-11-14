package com.chemmanager.service.mock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

import com.chemmanager.dto.PagedResult;
import com.chemmanager.dto.SalesQuery;
import com.chemmanager.model.sales.CashAccount;
import com.chemmanager.model.sales.DeliveryNote;
import com.chemmanager.model.sales.Invoice;
import com.chemmanager.model.sales.Quotation;
import com.chemmanager.model.sales.SalesOrder;
import com.chemmanager.service.SalesService;

/**
 * Demo sales history, used when the backend is not reachable.
 *
 * <p>The chain is the one the seeded database produces: a draft quotation, one waiting for its
 * order, one order that can be confirmed, one partly shipped and one already invoiced, plus an
 * issued invoice with a payment and a credit note. The actions mutate this dataset the same way the
 * server would, so the screen can be walked through end to end without a database.</p>
 */
public final class MockSalesService implements SalesService {

    private static final AtomicLong SEQUENCE = new AtomicLong(500);
    private static final List<Quotation> QUOTATIONS = new ArrayList<>();
    private static final List<SalesOrder> ORDERS = new ArrayList<>();
    private static final List<DeliveryNote> DELIVERIES = new ArrayList<>();
    private static final List<Invoice> INVOICES = new ArrayList<>();
    private static final List<Invoice.Payment> PAYMENTS = new ArrayList<>();
    private static final List<Invoice.CreditNote> CREDIT_NOTES = new ArrayList<>();

    static {
        QUOTATIONS.add(new Quotation(1, "DV-00001", 2, "Hôtel Atlas", "SENT", LocalDate.now().minusDays(6),
                LocalDate.now().plusDays(9), bd("36000.00"), bd("6840.00"), bd("42840.00"), "",
                List.of(line(1, 1, "PF-0002", "Liquide vaisselle citron 5 L", "PCE", 24, "1150.00", "0.0000"))));
        QUOTATIONS.add(new Quotation(2, "DV-00002", 1, "Supérette El Baraka", "DRAFT",
                LocalDate.now().minusDays(2), LocalDate.now().plusDays(13), bd("3840.00"), bd("729.60"),
                bd("4569.60"), "",
                List.of(line(2, 1, "PF-0001", "Liquide vaisselle citron 1 L", "PCE", 12, "320.00", "0.0000"))));
        QUOTATIONS.add(new Quotation(3, "DV-00003", 4, "Grossiste Oued Smar", "ACCEPTED",
                LocalDate.now().minusDays(12), LocalDate.now().plusDays(3), bd("150000.00"), bd("28500.00"),
                bd("178500.00"), "",
                List.of(line(3, 1, "PF-0003", "Nettoyant sols lavande 5 L", "PCE", 150, "1000.00", "0.0000"))));

        ORDERS.add(new SalesOrder(1, "SO-00001", 3, "Épicerie Amine", "CL-0003", "DRAFT",
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(3), bd("3360.00"), bd("638.40"),
                bd("3998.40"), bd("12.000"), bd("0.000"), bd("0.000"), 0, 0, "",
                List.of(orderLine(1, 1, "PF-0001", "Liquide vaisselle citron 1 L", "PCE", "12.000", "0.000", "0.000",
                        "280.00", "0.0000", "3360.00")),
                List.of(), List.of()));
        ORDERS.add(new SalesOrder(2, "SO-00002", 2, "Hôtel Atlas", "CL-0002", "PARTIAL",
                LocalDate.now().minusDays(9), LocalDate.now().minusDays(2), bd("23000.00"), bd("4370.00"),
                bd("27370.00"), bd("20.000"), bd("12.000"), bd("0.000"), 60.0, 0.0, "",
                List.of(orderLine(2, 2, "PF-0002", "Liquide vaisselle citron 5 L", "PCE", "20.000", "12.000",
                        "0.000", "1150.00", "0.0000", "23000.00")),
                List.of(new SalesOrder.Ref(1, "BL-00001", "DELIVERED", LocalDate.now().minusDays(2),
                        BigDecimal.ZERO)), List.of()));
        ORDERS.add(new SalesOrder(3, "SO-00003", 4, "Grossiste Oued Smar", "CL-0004", "INVOICED",
                LocalDate.now().minusDays(20), LocalDate.now().minusDays(14), bd("150000.00"), bd("28500.00"),
                bd("178500.00"), bd("150.000"), bd("150.000"), bd("150.000"), 100.0, 100.0, "",
                List.of(orderLine(3, 3, "PF-0003", "Nettoyant sols lavande 5 L", "PCE", "150.000", "150.000",
                        "150.000", "1000.00", "0.0000", "150000.00")),
                List.of(new SalesOrder.Ref(2, "BL-00002", "DELIVERED", LocalDate.now().minusDays(15),
                        BigDecimal.ZERO)),
                List.of(new SalesOrder.Ref(1, "FA-00001", "ISSUED", LocalDate.now().minusDays(14),
                        bd("178500.00")))));
        ORDERS.add(new SalesOrder(4, "SO-00004", 5, "Lycée El Hidhab", "CL-0005", "CONFIRMED",
                LocalDate.now().minusDays(3), LocalDate.now().plusDays(1), bd("4980.00"), bd("946.20"),
                bd("5926.20"), bd("6.000"), bd("0.000"), bd("0.000"), 0, 0, "",
                List.of(orderLine(4, 4, "PF-0004", "Eau de Javel 12° 2 L", "PCE", "6.000", "0.000", "0.000",
                        "830.00", "0.0000", "4980.00")),
                List.of(), List.of()));

        DELIVERIES.add(new DeliveryNote(1, "BL-00001", 2, "SO-00002", 2, "Hôtel Atlas", "DELIVERED",
                LocalDate.now().minusDays(2), LocalDate.now().minusDays(2), "M. Belkacem", null, "",
                bd("12.000"), 1,
                List.of(new DeliveryNote.Line(1, 2, "PF-0002", "Liquide vaisselle citron 5 L", "PCE", 11,
                        "PF2-2026-001", bd("12.000")))));
        DELIVERIES.add(new DeliveryNote(2, "BL-00002", 3, "SO-00003", 4, "Grossiste Oued Smar", "DELIVERED",
                LocalDate.now().minusDays(15), LocalDate.now().minusDays(14), "M. Haddad", null, "",
                bd("150.000"), 1,
                List.of(new DeliveryNote.Line(2, 3, "PF-0003", "Nettoyant sols lavande 5 L", "PCE", 12,
                        "PF3-2026-004", bd("150.000")))));

        INVOICES.add(new Invoice(1, "FA-00001", 4, "Grossiste Oued Smar", 3, "SO-00003", "ISSUED",
                LocalDate.now().minusDays(14), LocalDate.now().plusDays(16), bd("150000.00"), bd("28500.00"),
                bd("178500.00"), bd("0.00"), bd("178500.00"), true, false, 0, "",
                List.of(new Invoice.Line(1, 3, "PF-0003", "Nettoyant sols lavande 5 L", "PCE", "PF3-2026-004",
                        bd("150.000"), bd("1000.00"), bd("0.0000"), bd("19.0000"), bd("150000.00"))),
                List.of(), List.of(new Invoice.CreditNote(1, "AV-00001", 1, "FA-00001", 4,
                        "Grossiste Oued Smar", LocalDate.now().minusDays(3), "Retour de 5 bidons", bd("5000.00"),
                        bd("950.00"), bd("5950.00"), "ISSUED"))));
        INVOICES.add(new Invoice(2, "FA-00002", 2, "Hôtel Atlas", 0, null, "PAID",
                LocalDate.now().minusDays(40), LocalDate.now().minusDays(10), bd("20000.00"), bd("3800.00"),
                bd("23800.00"), bd("23800.00"), bd("0.00"), true, false, 2, "",
                List.of(new Invoice.Line(2, 2, "PF-0002", "Liquide vaisselle citron 5 L", "PCE", "PF2-2026-002",
                        bd("20.000"), bd("1000.00"), bd("0.0000"), bd("19.0000"), bd("20000.00"))),
                List.of(new Invoice.Payment(1, "ENC-00001", 2L, "FA-00002", 2, "Hôtel Atlas", bd("23800.00"),
                        LocalDate.now().minusDays(12), "TRANSFER", "Virement BNA", "Compte bancaire BNA")),
                List.of()));
    }

    // ---------------------------------------------------------------- quotations

    @Override
    public PagedResult<Quotation> quotations(SalesQuery query) {
        return page(QUOTATIONS, query, Quotation::status, quotation -> quotation.quoteNumber() + " "
                + quotation.customerName());
    }

    @Override
    public Quotation quotation(long id) {
        return QUOTATIONS.stream().filter(row -> row.id() == id).findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown quotation " + id));
    }

    @Override
    public Quotation createQuotation(Quotation.NewQuotation request) {
        long id = SEQUENCE.incrementAndGet();
        List<Quotation.Line> lines = new ArrayList<>();
        BigDecimal amountHt = BigDecimal.ZERO;
        long lineId = id * 10;
        for (Quotation.NewLine line : request.items()) {
            BigDecimal unitPrice = line.unitPrice() == null || line.unitPrice().signum() <= 0
                    ? bd("320.00") : line.unitPrice();
            BigDecimal discount = line.discountPct() == null ? BigDecimal.ZERO : line.discountPct();
            BigDecimal total = line.quantity().multiply(unitPrice)
                    .multiply(BigDecimal.valueOf(100).subtract(discount))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            amountHt = amountHt.add(total);
            lines.add(new Quotation.Line(lineId++, line.productId(), "PF-0001", "Article", "PCE",
                    line.quantity(), unitPrice, discount, bd("19.0000"), total));
        }
        BigDecimal vat = amountHt.multiply(bd("0.19")).setScale(2, RoundingMode.HALF_UP);
        Quotation created = new Quotation(id, String.format("DV-%05d", id % 100000), request.customerId(),
                "Client", "DRAFT", LocalDate.now(), request.validUntil(), amountHt, vat, amountHt.add(vat),
                request.notes() == null ? "" : request.notes(), lines);
        QUOTATIONS.add(0, created);

        return created;
    }

    @Override
    public Quotation sendQuotation(long id) {
        return replaceQuotation(quotation(id), "SENT");
    }

    @Override
    public Quotation acceptQuotation(long id) {
        Quotation accepted = replaceQuotation(quotation(id), "ACCEPTED");
        createOrder(new SalesOrder.NewOrder(accepted.customerId(), List.of(), LocalDate.now().plusDays(3),
                "Depuis le devis " + accepted.quoteNumber()));

        return accepted;
    }

    @Override
    public Quotation rejectQuotation(long id) {
        return replaceQuotation(quotation(id), "REJECTED");
    }

    // -------------------------------------------------------------------- orders

    @Override
    public PagedResult<SalesOrder> orders(SalesQuery query) {
        return page(ORDERS, query, SalesOrder::status, order -> order.orderNumber() + " " + order.customerName());
    }

    @Override
    public SalesOrder order(long id) {
        return ORDERS.stream().filter(row -> row.id() == id).findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown order " + id));
    }

    @Override
    public SalesOrder createOrder(SalesOrder.NewOrder request) {
        long id = SEQUENCE.incrementAndGet();
        List<SalesOrder.Line> lines = new ArrayList<>();
        BigDecimal amountHt = BigDecimal.ZERO;
        BigDecimal quantity = BigDecimal.ZERO;
        long lineId = id * 10;
        for (SalesOrder.NewLine line : request.items()) {
            BigDecimal unitPrice = bd("320.00");
            BigDecimal discount = line.discountPct() == null ? BigDecimal.ZERO : line.discountPct();
            BigDecimal total = line.quantity().multiply(unitPrice)
                    .multiply(BigDecimal.valueOf(100).subtract(discount))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            amountHt = amountHt.add(total);
            quantity = quantity.add(line.quantity());
            lines.add(orderLine(lineId++, line.productId(), "PF-0001", "Article", "PCE", line.quantity().toPlainString(),
                    "0.000", "0.000", unitPrice.toPlainString(), discount.toPlainString(), total.toPlainString()));
        }
        BigDecimal vat = amountHt.multiply(bd("0.19")).setScale(2, RoundingMode.HALF_UP);
        SalesOrder created = new SalesOrder(id, String.format("SO-%05d", id % 100000), request.customerId(),
                "Client", "CL-0000", "DRAFT", LocalDate.now(), request.deliveryDate(), amountHt, vat,
                amountHt.add(vat), quantity, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0,
                request.notes() == null ? "" : request.notes(), lines, List.of(), List.of());
        ORDERS.add(0, created);

        return created;
    }

    @Override
    public SalesOrder confirmOrder(long id) {
        return replaceOrder(order(id), "CONFIRMED");
    }

    @Override
    public SalesOrder cancelOrder(long id) {
        return replaceOrder(order(id), "CANCELLED");
    }

    // ---------------------------------------------------------------- deliveries

    @Override
    public PagedResult<DeliveryNote> deliveries(SalesQuery query) {
        return page(DELIVERIES, query, DeliveryNote::status,
                note -> note.noteNumber() + " " + note.customerName());
    }

    @Override
    public DeliveryNote delivery(long id) {
        return DELIVERIES.stream().filter(row -> row.id() == id).findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown delivery note " + id));
    }

    @Override
    public DeliveryNote createDelivery(long orderId, List<DeliveryLine> lines) {
        SalesOrder order = order(orderId);
        long id = SEQUENCE.incrementAndGet();
        List<DeliveryNote.Line> items = new ArrayList<>();
        BigDecimal shipped = BigDecimal.ZERO;
        for (SalesOrder.Line line : order.items()) {
            BigDecimal quantity = line.remaining();
            for (DeliveryLine requested : lines) {
                if (requested.orderItemId() == line.id()) {
                    quantity = requested.quantity();
                }
            }
            if (quantity.signum() <= 0) {
                continue;
            }
            shipped = shipped.add(quantity);
            items.add(new DeliveryNote.Line(id * 10 + items.size(), line.productId(), line.productCode(),
                    line.productName(), line.unit(), 11, "PF2-2026-001", quantity));
        }
        DeliveryNote note = new DeliveryNote(id, String.format("BL-%05d", id % 100000), orderId,
                order.orderNumber(), order.customerId(), order.customerName(), "PREPARED", LocalDate.now(), null,
                "", null, "", shipped, items.size(), items);
        DELIVERIES.add(0, note);

        List<SalesOrder.Line> updatedLines = order.items().stream()
                .map(line -> new SalesOrder.Line(line.id(), line.productId(), line.productCode(), line.productName(),
                        line.unit(), line.quantity(), line.quantity(), line.invoicedQty(), line.unitPrice(),
                        line.discountPct(), line.vatRate(), line.lineTotal()))
                .toList();
        List<SalesOrder.Ref> refs = new ArrayList<>(order.deliveries());
        refs.add(new SalesOrder.Ref(note.id(), note.noteNumber(), note.status(), note.preparedAt(), BigDecimal.ZERO));
        replaceOrderFields(new SalesOrder(order.id(), order.orderNumber(), order.customerId(), order.customerName(),
                order.customerCode(), "RESERVED", order.orderDate(), order.deliveryDate(), order.amountHt(),
                order.vatAmount(), order.totalTtc(), order.orderedQty(), order.orderedQty(), order.invoicedQty(),
                100.0, order.invoiceProgress(), order.notes(), updatedLines, refs, order.invoices()));

        return note;
    }

    @Override
    public DeliveryNote loadDelivery(long id) {
        return replaceDelivery(delivery(id), "LOADED", delivery(id).signedBy(), delivery(id).deliveredAt());
    }

    @Override
    public DeliveryNote signDelivery(long id, String signedBy, String notes) {
        DeliveryNote signed = replaceDelivery(delivery(id), "DELIVERED", signedBy, LocalDate.now());
        SalesOrder order = order(signed.orderId());
        replaceOrderFields(new SalesOrder(order.id(), order.orderNumber(), order.customerId(), order.customerName(),
                order.customerCode(), "PARTIAL", order.orderDate(), order.deliveryDate(), order.amountHt(),
                order.vatAmount(), order.totalTtc(), order.orderedQty(), order.orderedQty(), order.invoicedQty(),
                100.0, order.invoiceProgress(), order.notes(), order.items(),
                order.deliveries().stream().map(ref -> ref.id() == signed.id()
                        ? new SalesOrder.Ref(ref.id(), ref.number(), "DELIVERED", ref.date(), ref.total()) : ref)
                        .toList(), order.invoices()));

        return signed;
    }

    // ------------------------------------------------------------------ invoices

    @Override
    public PagedResult<Invoice> invoices(SalesQuery query) {
        return page(INVOICES, query, Invoice::status, invoice -> invoice.invoiceNumber() + " "
                + invoice.customerName());
    }

    @Override
    public Invoice invoice(long id) {
        return INVOICES.stream().filter(row -> row.id() == id).findFirst()
                .map(this::withMovements)
                .orElseThrow(() -> new IllegalStateException("Unknown invoice " + id));
    }

    @Override
    public Invoice createInvoice(long orderId) {
        SalesOrder order = order(orderId);
        long id = SEQUENCE.incrementAndGet();
        BigDecimal amountHt = order.items().stream().map(SalesOrder.Line::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal vat = amountHt.multiply(bd("0.19")).setScale(2, RoundingMode.HALF_UP);
        Invoice created = new Invoice(id, String.format("FA-%05d", id % 100000), order.customerId(),
                order.customerName(), orderId, order.orderNumber(), "DRAFT", LocalDate.now(),
                LocalDate.now().plusDays(30), amountHt, vat, amountHt.add(vat), BigDecimal.ZERO,
                amountHt.add(vat), false, false, 0, "",
                order.items().stream().map(line -> new Invoice.Line(line.id(), line.productId(), line.productCode(),
                        line.productName(), line.unit(), "PF2-2026-001", line.quantity(), line.unitPrice(),
                        line.discountPct(), line.vatRate(), line.lineTotal())).toList(),
                List.of(), List.of());
        INVOICES.add(0, created);

        return created;
    }

    @Override
    public Invoice issueInvoice(long id) {
        Invoice invoice = invoice(id);
        Invoice issued = new Invoice(invoice.id(), invoice.invoiceNumber(), invoice.customerId(),
                invoice.customerName(), invoice.orderId(), invoice.orderNumber(), "ISSUED", invoice.invoiceDate(),
                invoice.dueDate(), invoice.amountHt(), invoice.vatAmount(), invoice.totalTtc(),
                invoice.paidAmount(), invoice.remaining(), true, invoice.overdue(), invoice.printedCount(),
                invoice.notes(), invoice.items(), invoice.payments(), invoice.creditNotes());
        replaceInvoice(issued);

        return issued;
    }

    @Override
    public Invoice cancelInvoice(long id) {
        Invoice invoice = invoice(id);
        Invoice cancelled = new Invoice(invoice.id(), invoice.invoiceNumber(), invoice.customerId(),
                invoice.customerName(), invoice.orderId(), invoice.orderNumber(), "CANCELLED", invoice.invoiceDate(),
                invoice.dueDate(), invoice.amountHt(), invoice.vatAmount(), invoice.totalTtc(),
                invoice.paidAmount(), invoice.remaining(), invoice.finalized(), invoice.overdue(),
                invoice.printedCount(), invoice.notes(), invoice.items(), invoice.payments(),
                invoice.creditNotes());
        replaceInvoice(cancelled);

        return cancelled;
    }

    // ------------------------------------------------------------------ payments

    @Override
    public List<CashAccount> cashAccounts() {
        return List.of(new CashAccount(1, "CAISSE", "Caisse principale", "CASH", ""),
                new CashAccount(2, "BANQUE", "Compte bancaire BNA", "BANK", "BNA"));
    }

    @Override
    public Invoice.Payment pay(NewPayment request) {
        long id = SEQUENCE.incrementAndGet();
        Invoice.Payment payment = new Invoice.Payment(id, String.format("ENC-%05d", id % 100000),
                request.invoiceId() > 0 ? Long.valueOf(request.invoiceId()) : null,
                request.invoiceId() > 0 ? invoice(request.invoiceId()).invoiceNumber() : null,
                request.customerId(), "Client", request.amount(), LocalDate.now(),
                request.method() == null ? "CASH" : request.method(), request.reference(), "Caisse principale");
        PAYMENTS.add(payment);
        if (request.invoiceId() > 0) {
            Invoice invoice = invoice(request.invoiceId());
            BigDecimal paid = invoice.paidAmount().add(request.amount());
            String status = paid.compareTo(invoice.totalTtc()) >= 0 ? "PAID" : "PARTIAL";
            replaceInvoice(new Invoice(invoice.id(), invoice.invoiceNumber(), invoice.customerId(),
                    invoice.customerName(), invoice.orderId(), invoice.orderNumber(), status,
                    invoice.invoiceDate(), invoice.dueDate(), invoice.amountHt(), invoice.vatAmount(),
                    invoice.totalTtc(), paid, invoice.totalTtc().subtract(paid), invoice.finalized(),
                    invoice.overdue(), invoice.printedCount(), invoice.notes(), invoice.items(),
                    invoice.payments(), invoice.creditNotes()));
        }

        return payment;
    }

    // -------------------------------------------------------------- credit notes

    @Override
    public Invoice.CreditNote creditNote(long invoiceId, String reason, BigDecimal amountHt, BigDecimal vatRate) {
        long id = SEQUENCE.incrementAndGet();
        BigDecimal rate = vatRate == null ? bd("19") : vatRate;
        BigDecimal vat = amountHt.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        Invoice invoice = invoice(invoiceId);
        Invoice.CreditNote note = new Invoice.CreditNote(id, String.format("AV-%05d", id % 100000), invoiceId,
                invoice.invoiceNumber(), invoice.customerId(), invoice.customerName(), LocalDate.now(), reason,
                amountHt, vat, amountHt.add(vat), "ISSUED");
        CREDIT_NOTES.add(note);
        replaceInvoice(new Invoice(invoice.id(), invoice.invoiceNumber(), invoice.customerId(),
                invoice.customerName(), invoice.orderId(), invoice.orderNumber(), invoice.status(),
                invoice.invoiceDate(), invoice.dueDate(), invoice.amountHt(), invoice.vatAmount(),
                invoice.totalTtc(), invoice.paidAmount(), invoice.remaining(), invoice.finalized(),
                invoice.overdue(), invoice.printedCount(), invoice.notes(), invoice.items(),
                invoice.payments(), concat(invoice.creditNotes(), note)));

        return note;
    }

    @Override
    public Invoice.CreditNote applyCreditNote(long id) {
        Invoice.CreditNote note = CREDIT_NOTES.stream().filter(row -> row.id() == id).findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown credit note " + id));
        Invoice.CreditNote applied = new Invoice.CreditNote(note.id(), note.creditNumber(), note.invoiceId(),
                note.invoiceNumber(), note.customerId(), note.customerName(), note.creditDate(), note.reason(),
                note.amountHt(), note.vatAmount(), note.totalTtc(), "APPLIED");
        CREDIT_NOTES.replaceAll(row -> row.id() == id ? applied : row);
        Invoice invoice = invoice(note.invoiceId());
        replaceInvoice(new Invoice(invoice.id(), invoice.invoiceNumber(), invoice.customerId(),
                invoice.customerName(), invoice.orderId(), invoice.orderNumber(), invoice.status(),
                invoice.invoiceDate(), invoice.dueDate(), invoice.amountHt(), invoice.vatAmount(),
                invoice.totalTtc(), invoice.paidAmount(), invoice.remaining(), invoice.finalized(),
                invoice.overdue(), invoice.printedCount(), invoice.notes(), invoice.items(),
                invoice.payments(), invoice.creditNotes().stream().map(row -> row.id() == id ? applied : row)
                        .toList()));

        return applied;
    }

    @Override
    public Invoice.CreditNote cancelCreditNote(long id) {
        Invoice.CreditNote note = CREDIT_NOTES.stream().filter(row -> row.id() == id).findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown credit note " + id));
        Invoice.CreditNote cancelled = new Invoice.CreditNote(note.id(), note.creditNumber(), note.invoiceId(),
                note.invoiceNumber(), note.customerId(), note.customerName(), note.creditDate(), note.reason(),
                note.amountHt(), note.vatAmount(), note.totalTtc(), "CANCELLED");
        CREDIT_NOTES.replaceAll(row -> row.id() == id ? cancelled : row);

        return cancelled;
    }

    // -------------------------------------------------------------------- helpers

    private Invoice withMovements(Invoice invoice) {
        List<Invoice.Payment> payments = PAYMENTS.stream()
                .filter(payment -> payment.invoiceId() != null && payment.invoiceId() == invoice.id())
                .toList();
        List<Invoice.CreditNote> notes = CREDIT_NOTES.stream()
                .filter(note -> note.invoiceId() == invoice.id())
                .toList();

        return new Invoice(invoice.id(), invoice.invoiceNumber(), invoice.customerId(), invoice.customerName(),
                invoice.orderId(), invoice.orderNumber(), invoice.status(), invoice.invoiceDate(),
                invoice.dueDate(), invoice.amountHt(), invoice.vatAmount(), invoice.totalTtc(),
                invoice.paidAmount(), invoice.remaining(), invoice.finalized(), invoice.overdue(),
                invoice.printedCount(), invoice.notes(), invoice.items(), payments, notes);
    }

    private Quotation replaceQuotation(Quotation current, String status) {
        Quotation updated = new Quotation(current.id(), current.quoteNumber(), current.customerId(),
                current.customerName(), status, current.quoteDate(), current.validUntil(), current.amountHt(),
                current.vatAmount(), current.totalTtc(), current.notes(), current.items());
        QUOTATIONS.replaceAll(row -> row.id() == current.id() ? updated : row);

        return updated;
    }

    private SalesOrder replaceOrder(SalesOrder current, String status) {
        return replaceOrderFields(new SalesOrder(current.id(), current.orderNumber(), current.customerId(),
                current.customerName(), current.customerCode(), status, current.orderDate(), current.deliveryDate(),
                current.amountHt(), current.vatAmount(), current.totalTtc(), current.orderedQty(),
                current.deliveredQty(), current.invoicedQty(), current.deliveryProgress(), current.invoiceProgress(),
                current.notes(), current.items(), current.deliveries(), current.invoices()));
    }

    private SalesOrder replaceOrderFields(SalesOrder updated) {
        ORDERS.replaceAll(row -> row.id() == updated.id() ? updated : row);

        return updated;
    }

    private DeliveryNote replaceDelivery(DeliveryNote current, String status, String signedBy, LocalDate deliveredAt) {
        DeliveryNote updated = new DeliveryNote(current.id(), current.noteNumber(), current.orderId(),
                current.orderNumber(), current.customerId(), current.customerName(), status, current.preparedAt(),
                deliveredAt, signedBy == null ? "" : signedBy, current.routeNumber(), current.notes(),
                current.totalQuantity(), current.lineCount(), current.items());
        DELIVERIES.replaceAll(row -> row.id() == current.id() ? updated : row);

        return updated;
    }

    private void replaceInvoice(Invoice updated) {
        INVOICES.replaceAll(row -> row.id() == updated.id() ? updated : row);
        for (Invoice.Payment payment : updated.payments()) {
            if (PAYMENTS.stream().noneMatch(row -> row.id() == payment.id())) {
                PAYMENTS.add(payment);
            }
        }
        for (Invoice.CreditNote note : updated.creditNotes()) {
            if (CREDIT_NOTES.stream().noneMatch(row -> row.id() == note.id())) {
                CREDIT_NOTES.add(note);
            }
        }
    }

    private static List<Invoice.CreditNote> concat(List<Invoice.CreditNote> notes, Invoice.CreditNote note) {
        List<Invoice.CreditNote> merged = new ArrayList<>(notes);
        merged.add(note);

        return merged;
    }

    /** Filters, sorts and paginates a demo list the way the server would. */
    private static <T> PagedResult<T> page(List<T> rows, SalesQuery query,
                                           java.util.function.Function<T, String> status,
                                           java.util.function.Function<T, String> searchable) {
        SalesQuery effective = query == null ? SalesQuery.initial(25) : query;
        String needle = effective.search().toLowerCase(Locale.ROOT);
        List<T> matches = rows.stream()
                .filter(row -> effective.status() == null || effective.status().isBlank()
                        || effective.status().equalsIgnoreCase(status.apply(row)))
                .filter(row -> needle.isEmpty()
                        || searchable.apply(row).toLowerCase(Locale.ROOT).contains(needle))
                .toList();
        int from = Math.min(matches.size(), effective.page() * effective.pageSize());
        int to = Math.min(matches.size(), from + effective.pageSize());

        return new PagedResult<>(matches.subList(from, to), effective.page(), effective.pageSize(), matches.size());
    }

    private static Quotation.Line line(long id, long productId, String code, String name, String unit,
                                       int quantity, String unitPrice, String discount) {
        BigDecimal price = bd(unitPrice);
        BigDecimal total = BigDecimal.valueOf(quantity).multiply(price)
                .multiply(BigDecimal.valueOf(100).subtract(bd(discount)))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        return new Quotation.Line(id, productId, code, name, unit, BigDecimal.valueOf(quantity), price,
                bd(discount), bd("19.0000"), total);
    }

    private static SalesOrder.Line orderLine(long id, long productId, String code, String name, String unit,
                                             String quantity, String delivered, String invoiced, String unitPrice,
                                             String discount, String lineTotal) {
        return new SalesOrder.Line(id, productId, code, name, unit, bd(quantity), bd(delivered), bd(invoiced),
                bd(unitPrice), bd(discount), bd("19.0000"), bd(lineTotal));
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
