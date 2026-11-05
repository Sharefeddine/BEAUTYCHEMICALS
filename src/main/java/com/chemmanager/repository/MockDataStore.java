package com.chemmanager.repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import com.chemmanager.model.NotificationItem;
import com.chemmanager.model.Product;
import com.chemmanager.model.ProductType;
import com.chemmanager.model.RawMaterial;
import com.chemmanager.model.Severity;
import com.chemmanager.model.User;
import com.chemmanager.model.analytics.ActivityEntry;
import com.chemmanager.security.DefaultRoles;
import com.chemmanager.security.PasswordHasher;
import com.chemmanager.security.Role;

/**
 * Deterministic demo dataset used when the application runs in MOCK mode.
 *
 * <p>It is deliberately separate from the production services: every record below is a
 * realistic business transaction (sales lines, purchases, expenses, production orders,
 * batches) that the mock services aggregate exactly like the SQL backend aggregates rows.
 * Nothing on the dashboard is hardcoded — the KPIs are computed from these collections.</p>
 */
public final class MockDataStore {

    // --------------------------------------------------------------------- records

    public record SalesRecord(LocalDate date, long productId, String sku, String productName, String category,
                              BigDecimal quantity, BigDecimal unitPrice, BigDecimal unitCost, BigDecimal vatRate,
                              long customerId, String customerName, String representative, boolean paid) {

        public BigDecimal revenue() {
            return quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
        }

        public BigDecimal cost() {
            return quantity.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
        }

        public BigDecimal vat() {
            return revenue().multiply(vatRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }

        public BigDecimal totalTtc() {
            return revenue().add(vat());
        }
    }

    public record PurchaseRecord(LocalDate date, long supplierId, String supplierName, String category,
                                 BigDecimal amountHt, BigDecimal vatAmount, boolean paid) {

        public BigDecimal totalTtc() {
            return amountHt.add(vatAmount);
        }
    }

    public record ExpenseRecord(LocalDate date, String category, String label, BigDecimal amount,
                                boolean paid, String paymentMethod) {
    }

    public record ProductionRecord(LocalDate date, String orderNumber, long productId, String productName,
                                   BigDecimal plannedQty, BigDecimal actualQty, BigDecimal wasteQty,
                                   String status, BigDecimal materialCost, BigDecimal packagingCost,
                                   BigDecimal otherCost) {

        public BigDecimal totalCost() {
            return materialCost.add(packagingCost).add(otherCost);
        }

        public boolean isOpen() {
            return !("COMPLETED".equals(status) || "REJECTED".equals(status) || "CANCELLED".equals(status));
        }
    }

    public record CustomerRecord(long id, String code, String name, String type, String wilaya, String phone,
                                 BigDecimal creditLimit, BigDecimal balance, String category) {
    }

    public record SupplierRecord(long id, String code, String name, String contact, String phone, String email,
                                 String paymentTerms, BigDecimal balance) {
    }

    public record BatchRecord(String itemCode, String itemName, String batchNumber, BigDecimal quantity,
                              String unit, LocalDate expiryDate, String warehouse, boolean finishedProduct) {
    }

    public record Credential(String username, String hash) {
    }

    // --------------------------------------------------------------------- state

    private static final MockDataStore INSTANCE = new MockDataStore();

    private final List<Product> products = new ArrayList<>();
    private final List<RawMaterial> rawMaterials = new ArrayList<>();
    private final List<SalesRecord> sales = new ArrayList<>();
    private final List<PurchaseRecord> purchases = new ArrayList<>();
    private final List<ExpenseRecord> expenses = new ArrayList<>();
    private final List<ProductionRecord> production = new ArrayList<>();
    private final List<CustomerRecord> customers = new ArrayList<>();
    private final List<SupplierRecord> suppliers = new ArrayList<>();
    private final List<BatchRecord> batches = new ArrayList<>();
    private final List<User> users = new ArrayList<>();
    private final List<ActivityEntry> activities = new ArrayList<>();
    private final Map<String, String> credentials = new LinkedHashMap<>();
    private final LocalDate referenceDate = LocalDate.now();

    private MockDataStore() {
        seedUsers();
        seedProducts();
        seedRawMaterials();
        seedCustomers();
        seedSuppliers();
        seedBatches();
        seedSales();
        seedPurchases();
        seedExpenses();
        seedProduction();
        seedActivities();
    }

    public static MockDataStore get() {
        return INSTANCE;
    }

    public LocalDate referenceDate() {
        return referenceDate;
    }

    // --------------------------------------------------------------------- accessors

    public List<Product> products() {
        return List.copyOf(products);
    }

    public List<RawMaterial> rawMaterials() {
        return List.copyOf(rawMaterials);
    }

    public List<SalesRecord> sales() {
        return List.copyOf(sales);
    }

    public List<PurchaseRecord> purchases() {
        return List.copyOf(purchases);
    }

    public List<ExpenseRecord> expenses() {
        return List.copyOf(expenses);
    }

    public List<ProductionRecord> production() {
        return List.copyOf(production);
    }

    public List<CustomerRecord> customers() {
        return List.copyOf(customers);
    }

    public List<SupplierRecord> suppliers() {
        return List.copyOf(suppliers);
    }

    public List<BatchRecord> batches() {
        return List.copyOf(batches);
    }

    public List<User> users() {
        return List.copyOf(users);
    }

    public List<ActivityEntry> activities() {
        return List.copyOf(activities);
    }

    public Optional<User> findUser(String username) {
        return users.stream().filter(u -> u.username().equalsIgnoreCase(username)).findFirst();
    }

    public Optional<String> passwordHash(String username) {
        return Optional.ofNullable(credentials.get(username.toLowerCase()));
    }

    public List<String> productCategories() {
        return products.stream().map(Product::category).distinct().sorted().toList();
    }

    public List<String> materialCategories() {
        return rawMaterials.stream().map(RawMaterial::category).distinct().sorted().toList();
    }

    public void replaceProduct(Product product) {
        for (int i = 0; i < products.size(); i++) {
            if (products.get(i).id() == product.id()) {
                products.set(i, product);
                return;
            }
        }
        products.add(product);
    }

    // --------------------------------------------------------------------- seeds

    private void seedUsers() {
        PasswordHasher hasher = new PasswordHasher();
        addUser(1, "admin", "Yacine Belkacem", "y.belkacem@chempro.dz", "+213 661 22 33 44",
                "Super Administrateur", "admin123", DefaultRoles.superAdmin());
        addUser(2, "manager", "Salima Haddad", "s.haddad@chempro.dz", "+213 770 11 22 33",
                "Directrice Générale", "manager123", DefaultRoles.generalManager());
        addUser(3, "production", "Karim Ferhat", "k.ferhat@chempro.dz", "+213 551 44 55 66",
                "Responsable Production", "prod123", DefaultRoles.productionManager());
        addUser(4, "store", "Nadia Bounouh", "n.bounouh@chempro.dz", "+213 553 77 88 99",
                "Responsable Entrepôt", "store123", DefaultRoles.warehouseManager());
        addUser(5, "sales", "Amine Cherifi", "a.cherifi@chempro.dz", "+213 660 33 44 55",
                "Responsable Commercial", "sales123", DefaultRoles.salesManager());
        addUser(6, "accounting", "Farida Ziani", "f.ziani@chempro.dz", "+213 771 55 66 77",
                "Comptable", "acc123", DefaultRoles.accountant());
    }

    private void addUser(long id, String username, String fullName, String email, String phone,
                         String jobTitle, String password, Role role) {
        credentials.put(username.toLowerCase(), new PasswordHasher().hash(password.toCharArray()));
        users.add(new User(id, username, fullName, email, phone, jobTitle, List.of(role), true,
                LocalDateTime.now().minusDays(id).withHour(8).withMinute(15)));
    }

    private void seedProducts() {
        ProductType m = ProductType.MANUFACTURED;
        ProductType t = ProductType.TRADED;
        product("DISH-500", "Liquide vaisselle Citron 500 ml", "سائل غسل الأواني بالليمون 500 مل",
                "DISHWASHING", "Bidon 500 ml", 500, 24, 55, 95, 115, 480, 150, 1800, m);
        product("DISH-1L", "Liquide vaisselle Citron 1 L", "سائل غسل الأواني بالليمون 1 لتر",
                "DISHWASHING", "Bouteille 1 L", 1000, 12, 88, 160, 190, 260, 120, 1500, m);
        product("DISH-5L", "Liquide vaisselle Citron 5 L", "سائل غسل الأواني بالليمون 5 لتر",
                "DISHWASHING", "Bidon 5 L", 5000, 4, 395, 720, 850, 55, 80, 400, m);
        product("DISH-20L", "Liquide vaisselle Citron 20 L", "سائل غسل الأواني بالليمون 20 لتر",
                "DISHWASHING", "Fût 20 L", 20000, 1, 1420, 2680, 3100, 26, 10, 120, m);
        product("FLOOR-1L", "Nettoyant sol Lavande 1 L", "منظف الأرضيات بالخزامى 1 لتر",
                "FLOOR_CLEANER", "Bouteille 1 L", 1000, 12, 96, 175, 210, 310, 100, 1200, m);
        product("FLOOR-5L", "Nettoyant sol Lavande 5 L", "منظف الأرضيات بالخزامى 5 لتر",
                "FLOOR_CLEANER", "Bidon 5 L", 5000, 4, 430, 790, 920, 140, 60, 350, m);
        product("FLOOR-20L", "Nettoyant sol Lavande 20 L", "منظف الأرضيات بالخزامى 20 لتر",
                "FLOOR_CLEANER", "Fût 20 L", 20000, 1, 1580, 2890, 3250, 12, 8, 90, m);
        product("BLEACH-1L", "Eau de Javel 12° 1 L", "ماء جافيل 12 درجة 1 لتر",
                "BLEACH", "Bouteille 1 L", 1000, 12, 52, 105, 130, 620, 200, 2000, m);
        product("BLEACH-5L", "Eau de Javel 12° 5 L", "ماء جافيل 12 درجة 5 لتر",
                "BLEACH", "Bidon 5 L", 5000, 4, 235, 450, 520, 175, 70, 500, m);
        product("BLEACH-20L", "Eau de Javel 12° 20 L", "ماء جافيل 12 درجة 20 لتر",
                "BLEACH", "Fût 20 L", 20000, 1, 870, 1650, 1890, 6, 10, 80, m);
        product("DISINF-1L", "Désinfectant surfaces 1 L", "مطهر الأسطح 1 لتر",
                "DISINFECTANT", "Bouteille 1 L", 1000, 12, 132, 255, 295, 210, 90, 900, m);
        product("DISINF-5L", "Désinfectant surfaces 5 L", "مطهر الأسطح 5 لتر",
                "DISINFECTANT", "Bidon 5 L", 5000, 4, 610, 1120, 1280, 74, 40, 300, m);
        product("SOAP-500", "Savon liquide mains 500 ml", "صابون سائل لليدين 500 مل",
                "LIQUID_SOAP", "Bidon 500 ml", 500, 24, 68, 140, 165, 540, 160, 1500, m);
        product("SOAP-5L", "Savon liquide mains 5 L", "صابون سائل لليدين 5 لتر",
                "LIQUID_SOAP", "Bidon 5 L", 5000, 4, 520, 985, 1120, 96, 40, 260, m);
        product("LAUND-2L", "Lessive liquide Fleur 2 L", "منظف الغسيل السائل بالزهور 2 لتر",
                "LAUNDRY", "Bidon 2 L", 2000, 6, 215, 405, 465, 320, 120, 800, m);
        product("LAUND-5L", "Lessive liquide Fleur 5 L", "منظف الغسيل السائل بالزهور 5 لتر",
                "LAUNDRY", "Bidon 5 L", 5000, 4, 495, 910, 1060, 88, 50, 320, m);
        product("GLASS-750", "Nettoyant vitres 750 ml", "منظف الزجاج 750 مل",
                "GLASS_CLEANER", "Bouteille 750 ml", 750, 12, 98, 190, 225, 0, 60, 700, m);
        product("GLASS-5L", "Nettoyant vitres 5 L", "منظف الزجاج 5 لتر",
                "GLASS_CLEANER", "Bidon 5 L", 5000, 4, 470, 890, 1040, 42, 30, 220, m);
        product("DEGR-1L", "Dégraissant industriel 1 L", "مزيل الشحوم الصناعي 1 لتر",
                "DEGREASER", "Bouteille 1 L", 1000, 12, 245, 465, 530, 130, 50, 500, m);
        product("DEGR-20L", "Dégraissant industriel 20 L", "مزيل الشحوم الصناعي 20 لتر",
                "DEGREASER", "Fût 20 L", 20000, 1, 3850, 7250, 8100, 9, 6, 60, m);
        product("IND-25KG", "Détergent industriel poudre 25 kg", "منظف صناعي مسحوق 25 كغ",
                "INDUSTRIAL", "Sac 25 kg", 25000, 1, 3250, 5650, 6350, 34, 15, 150, m);
        product("PACK-BTL1L", "Bouteille PET 1 L (vide)", "قارورة بلاستيكية 1 لتر",
                "PACKAGING", "Carton 60 pcs", 1000, 60, 16, 28, 34, 2400, 800, 8000, t);
        product("PACK-CAP28", "Bouchon 28 mm", "سداد 28 مم",
                "PACKAGING", "Sac 1000 pcs", 0, 1000, 3, 7, 9, 5600, 2000, 20000, t);
    }

    private void product(String sku, String nameFr, String nameAr, String category, String packaging,
                         double capacity, int perCarton, double productionCost, double wholesale, double retail,
                         int stock, int min, int max, ProductType type) {
        long id = products.size() + 1;
        BigDecimal purchase = bd(productionCost * 0.72);
        products.add(new Product(id, sku, "613" + String.format("%010d", id * 7919L), nameFr, nameAr,
                nameFr + " — " + category, category, type, "PCE", packaging, bd(capacity), perCarton,
                min, max, purchase, bd(productionCost), bd(wholesale), bd(retail), bd(wholesale * 0.92),
                BigDecimal.valueOf(19), stock, null, !"GLASS-5L".equals(sku)));
    }

    private void seedRawMaterials() {
        raw("MP-001", "LABSA 96%", "Acide alkylbenzène sulfonique", "SURFACTANT", "Chimie Algérie SPA",
                "KG", "KG", 148, 4200, 900, "A-01-02", "B-240712", -260, 520, "CORROSIVE",
                "GHS05,GHS07", "Local ventilé, 5-30 °C, à l'abri de l'humidité", true);
        raw("MP-002", "SLS Poudre 90%", "Sodium lauryl sulfate", "SURFACTANT", "Chimie Algérie SPA",
                "KG", "KG", 268, 1800, 500, "A-01-03", "B-240805", -120, 480, "IRRITANT",
                "GHS07", "Local sec, température ambiante", true);
        raw("MP-003", "Soude caustique 99%", "Hydroxyde de sodium", "CHEMICAL", "Sotrachem",
                "KG", "KG", 92, 950, 300, "A-02-01", "B-240930", -60, 210, "CORROSIVE",
                "GHS05", "Récipient étanche, local ventilé, hygroscopique", true);
        raw("MP-004", "Hypochlorite de sodium 12%", "Hypochlorite de sodium", "CHEMICAL", "Sotrachem",
                "L", "L", 48, 2600, 700, "A-02-02", "B-240915", -45, 150, "CORROSIVE",
                "GHS05,GHS09", "À l'abri de la lumière et de la chaleur, < 25 °C", true);
        raw("MP-005", "Acide chlorhydrique 33%", "Acide chlorhydrique", "CHEMICAL", "Sotrachem",
                "L", "L", 39, 800, 250, "A-02-03", "B-240618", -30, 90, "CORROSIVE",
                "GHS05,GHS07", "Local ventilé, bac de rétention", true);
        raw("MP-006", "Sel de cuisine raffiné", "Chlorure de sodium", "CHEMICAL", "Salins de l'Est",
                "KG", "KG", 12, 5000, 1000, "A-03-01", "B-241001", -700, 900, "NONE",
                "", "Local sec", false);
        raw("MP-007", "Parfum Citron P-118", "Mélange d'arômes", "FRAGRANCE", "Parfums & Arômes SARL",
                "KG", "KG", 2850, 120, 40, "B-01-01", "B-240820", -200, 340, "FLAMMABLE",
                "GHS02", "Local frais, loin des sources de chaleur", true);
        raw("MP-008", "Parfum Lavande P-204", "Mélange d'arômes", "FRAGRANCE", "Parfums & Arômes SARL",
                "KG", "KG", 3100, 85, 30, "B-01-02", "B-240822", -210, 330, "FLAMMABLE",
                "GHS02", "Local frais, loin des sources de chaleur", true);
        raw("MP-009", "Colorant Bleu Vif", "Colorant alimentaire E133", "COLORANT", "Colorants Maghreb",
                "KG", "KG", 1450, 26, 12, "B-02-01", "B-240703", -520, 610, "IRRITANT",
                "GHS07", "Local sec, à l'abri de la lumière", true);
        raw("MP-010", "Colorant Vert E102/E133", "Mélange de colorants", "COLORANT", "Colorants Maghreb",
                "KG", "KG", 1380, 14, 10, "B-02-02", "B-240706", -540, 620, "IRRITANT",
                "GHS07", "Local sec, à l'abri de la lumière", true);
        raw("MP-011", "Cocamide DEA", "Cocamide diethanolamine", "ADDITIVE", "Chimie Algérie SPA",
                "KG", "KG", 620, 340, 120, "A-04-01", "B-240711", -300, 450, "HARMFUL",
                "GHS07,GHS09", "Local ventilé, 15-25 °C", true);
        raw("MP-012", "Glycérine végétale", "Glycérol", "ADDITIVE", "Chimie Algérie SPA",
                "KG", "KG", 380, 210, 80, "A-04-02", "B-240725", -400, 520, "NONE",
                "", "Local sec", true);
        raw("MP-013", "EDTA-4Na", "Éthylènediaminetétraacétate de sodium", "ADDITIVE", "Sotrachem",
                "KG", "KG", 490, 95, 50, "A-04-03", "B-240612", -620, 700, "IRRITANT",
                "GHS07", "Local sec", true);
        raw("MP-014", "Épaississant CAB-35", "Cocamidopropyl bétaïne", "ADDITIVE", "Chimie Algérie SPA",
                "KG", "KG", 540, 180, 70, "A-04-04", "B-240718", -350, 480, "IRRITANT",
                "GHS07", "Local ventilé", true);
        raw("MP-015", "Conservateur CMIT/MIT", "Mélange isothiazolinones", "ADDITIVE", "Sotrachem",
                "KG", "KG", 1980, 22, 12, "A-05-01", "B-240801", -260, 380, "TOXIC",
                "GHS06,GHS09", "Local ventilé, gants et lunettes obligatoires", true);
        raw("MP-016", "Opacifiant OP-30", "Dispersion de styrène acrylique", "ADDITIVE", "Sotrachem",
                "KG", "KG", 720, 60, 30, "A-05-02", "B-240803", -310, 420, "IRRITANT",
                "GHS07", "Local sec, éviter le gel", true);
        raw("MP-017", "Carbonate de sodium", "Carbonate de sodium", "CHEMICAL", "Sotrachem",
                "KG", "KG", 78, 3200, 800, "A-03-02", "B-240912", -720, 800, "IRRITANT",
                "GHS07", "Local sec", true);
        raw("MP-018", "Sulfate de sodium", "Sulfate de sodium anhydre", "CHEMICAL", "Sotrachem",
                "KG", "KG", 65, 2800, 700, "A-03-03", "B-240910", -730, 800, "NONE",
                "", "Local sec", true);
        raw("MP-019", "Alcool éthylique 96°", "Éthanol", "SOLVENT", "Sotrachem",
                "L", "L", 320, 900, 250, "A-06-01", "B-241002", -180, 260, "FLAMMABLE",
                "GHS02", "Local ventilé, à l'écart des flammes, < 25 °C", true);
        raw("MP-020", "Isopropanol 99%", "Propan-2-ol", "SOLVENT", "Sotrachem",
                "L", "L", 410, 460, 150, "A-06-02", "B-240928", -220, 300, "FLAMMABLE",
                "GHS02,GHS07", "Local ventilé, à l'écart des flammes", true);
        raw("MP-021", "Bouteille PET 1 L vide", "Polyéthylène téréphtalate", "PACKAGING", "Packaging DZ",
                "PCE", "PCE", 16, 12400, 4000, "C-01-01", "B-241005", -2000, 300, "NONE",
                "", "Zone de stockage packaging, à l'abri du soleil", false);
        raw("MP-022", "Bidon 5 L vide", "Polyéthylène haute densité", "PACKAGING", "Packaging DZ",
                "PCE", "PCE", 42, 3200, 1500, "C-01-02", "B-241005", -2000, 300, "NONE",
                "", "Zone de stockage packaging", false);
        raw("MP-023", "Fût 20 L vide", "Polyéthylène haute densité", "PACKAGING", "Packaging DZ",
                "PCE", "PCE", 185, 620, 300, "C-01-03", "B-241006", -2000, 300, "NONE",
                "", "Zone de stockage packaging", false);
        raw("MP-024", "Bouchon 28 mm", "Polypropylène", "PACKAGING", "Packaging DZ",
                "PCE", "PCE", 3, 38000, 12000, "C-02-01", "B-241006", -3000, 300, "NONE",
                "", "Zone de stockage packaging", false);
        raw("MP-025", "Étiquette adhésive 1 L", "Film polypropylène imprimé", "PACKAGING", "Imprimerie Nord",
                "PCE", "PCE", 5, 9500, 4000, "C-02-02", "B-240918", -900, 300, "NONE",
                "", "Local sec, 15-25 °C", false);
        raw("MP-026", "Carton 12 bouteilles", "Carton ondulé double cannelure", "PACKAGING", "Imprimerie Nord",
                "PCE", "PCE", 68, 1400, 600, "C-03-01", "B-240919", -1500, 300, "NONE",
                "", "Local sec", false);
        raw("MP-027", "Bicarbonate de sodium", "Hydrogénocarbonate de sodium", "CHEMICAL", "Sotrachem",
                "KG", "KG", 88, 640, 300, "A-03-04", "B-240915", -700, 800, "NONE",
                "", "Local sec", true);
        raw("MP-028", "Soude en perles", "Hydroxyde de sodium perlé", "CHEMICAL", "Sotrachem",
                "KG", "KG", 105, 280, 200, "A-02-04", "B-240925", -45, 150, "CORROSIVE",
                "GHS05", "Récipient étanche", true);
    }

    /** {@code productionOffsetDays} is relative to today (negative = produced in the past). */
    private void raw(String code, String commercialName, String chemicalName, String category, String supplier,
                     String purchaseUnit, String storageUnit, double price, double stock, double minStock,
                     String location, String batch, int productionOffsetDays, int expiryOffsetDays,
                     String hazardClass, String pictograms, String storageConditions, boolean sds) {
        long id = rawMaterials.size() + 1;
        BigDecimal stockValue = bd(stock);
        rawMaterials.add(new RawMaterial(id, code, commercialName, chemicalName, category, supplier,
                purchaseUnit, storageUnit, BigDecimal.ONE, bd(price), stockValue, bd(minStock), location, batch,
                referenceDate.plusDays(productionOffsetDays), referenceDate.plusDays(expiryOffsetDays),
                hazardClass, pictograms, storageConditions, sds, sds ? code + "_SDS_FR.pdf" : null, true));
    }

    private void seedCustomers() {
        String[][] data = {
                {"CLI-001", "SARL Nour Distribution", "COMPANY", "Sétif", "0770 11 22 33", "GROSSISTE"},
                {"CLI-002", "Supermarché Al Fath", "COMPANY", "Sétif", "0771 22 33 44", "GRANDE_SURFACE"},
                {"CLI-003", "Épicerie Amel", "INDIVIDUAL", "Sétif", "0661 33 44 55", "DETAIL"},
                {"CLI-004", "Supérette El Baraka", "COMPANY", "Constantine", "0772 44 55 66", "DETAIL"},
                {"CLI-005", "Groupe Djurdjura Hygiène", "COMPANY", "Alger", "0773 55 66 77", "GROSSISTE"},
                {"CLI-006", "Marché Couvert Batna", "COMPANY", "Batna", "0662 66 77 88", "DETAIL"},
                {"CLI-007", "Boulangerie Les Blés d'Or", "INDIVIDUAL", "Sétif", "0551 77 88 99", "PROFESSIONNEL"},
                {"CLI-008", "Hôtel El Hidhab", "COMPANY", "Sétif", "036 84 55 66", "HOTELLERIE"},
                {"CLI-009", "Clinique Es-Salem", "COMPANY", "Bordj Bou Arreridj", "0774 88 99 00", "INSTITUTION"},
                {"CLI-010", "Lycée Mohamed Boudiaf", "INSTITUTION", "Sétif", "036 84 66 77", "INSTITUTION"},
                {"CLI-011", "SARL Propreté Plus", "COMPANY", "Alger", "0775 99 00 11", "PROFESSIONNEL"},
                {"CLI-012", "Supérette Nour El Ain", "COMPANY", "Biskra", "0663 00 11 22", "DETAIL"},
                {"CLI-013", "Dépôt Tlemcen Ouest", "COMPANY", "Tlemcen", "0776 11 22 33", "GROSSISTE"},
                {"CLI-014", "Café Central Annaba", "INDIVIDUAL", "Annaba", "0777 22 33 44", "PROFESSIONNEL"},
                {"CLI-015", "Résidence Les Oliviers", "COMPANY", "Blida", "0664 33 44 55", "INSTITUTION"},
                {"CLI-016", "Épicerie Hachemi", "INDIVIDUAL", "Sétif", "0665 44 55 66", "DETAIL"},
                {"CLI-017", "SARL Oran Clean", "COMPANY", "Oran", "0778 55 66 77", "PROFESSIONNEL"},
                {"CLI-018", "Marché Tizi Gros", "COMPANY", "Tizi Ouzou", "0779 66 77 88", "GROSSISTE"},
                {"CLI-019", "Restaurant Le Gourmet", "INDIVIDUAL", "Sétif", "0550 77 88 99", "PROFESSIONNEL"},
                {"CLI-020", "Lycée Technique Sétif", "INSTITUTION", "Sétif", "036 84 77 88", "INSTITUTION"}
        };
        Random random = new Random(42);
        for (int i = 0; i < data.length; i++) {
            String[] row = data[i];
            BigDecimal balance = switch (row[5]) {
                case "GROSSISTE" -> bd(120_000 + random.nextInt(280_000));
                case "GRANDE_SURFACE" -> bd(60_000 + random.nextInt(120_000));
                case "DETAIL" -> bd(random.nextInt(45_000));
                default -> bd(random.nextInt(90_000));
            };
            customers.add(new CustomerRecord(i + 1, row[0], row[1], row[2], row[3], row[4],
                    bd("GROSSISTE".equals(row[5]) ? 1_500_000 : 300_000), balance, row[5]));
        }
    }

    private void seedSuppliers() {
        String[][] data = {
                {"FRN-001", "Chimie Algérie SPA", "M. Rachid Hamdi", "+213 36 45 12 78", "commercial@chimie-dz.com", "30 jours"},
                {"FRN-002", "Sotrachem", "Mme Leila Saadi", "+213 21 55 44 33", "ventes@sotrachem.dz", "45 jours"},
                {"FRN-003", "Parfums & Arômes SARL", "M. Sofiane Toumi", "+213 21 66 77 88", "contact@parfums-aromes.dz", "Comptant"},
                {"FRN-004", "Colorants Maghreb", "Mme Hania Benali", "+213 31 22 33 44", "info@colorants-maghreb.dz", "30 jours"},
                {"FRN-005", "Packaging DZ", "M. Walid Meziane", "+213 36 88 99 00", "commandes@packaging-dz.com", "60 jours"},
                {"FRN-006", "Imprimerie Nord", "Mme Samia Kaci", "+213 36 77 66 55", "devis@imprimerie-nord.dz", "30 jours"},
                {"FRN-007", "Salins de l'Est", "M. Nabil Brahimi", "+213 37 44 55 66", "contact@salins-est.dz", "Comptant"},
                {"FRN-008", "Transport & Logistique Sétif", "M. Fouad Larbi", "+213 36 11 22 33", "logistique@tls-dz.com", "15 jours"}
        };
        Random random = new Random(7);
        for (int i = 0; i < data.length; i++) {
            String[] row = data[i];
            suppliers.add(new SupplierRecord(i + 1, row[0], row[1], row[2], row[3], row[4], row[5],
                    bd(random.nextInt(480_000))));
        }
    }

    private void seedBatches() {
        RawMaterial hypochlorite = rawMaterials.stream().filter(r -> r.code().equals("MP-004")).findFirst().orElseThrow();
        RawMaterial perfume = rawMaterials.stream().filter(r -> r.code().equals("MP-007")).findFirst().orElseThrow();
        RawMaterial edta = rawMaterials.stream().filter(r -> r.code().equals("MP-013")).findFirst().orElseThrow();
        RawMaterial conserve = rawMaterials.stream().filter(r -> r.code().equals("MP-015")).findFirst().orElseThrow();
        batches.add(new BatchRecord(hypochlorite.code(), hypochlorite.commercialName(), "B-240915",
                bd(150), "L", referenceDate.plusDays(12), "MP - Matières premières", false));
        batches.add(new BatchRecord(perfume.code(), perfume.commercialName(), "B-240820",
                bd(18), "KG", referenceDate.plusDays(28), "MP - Matières premières", false));
        batches.add(new BatchRecord(edta.code(), edta.commercialName(), "B-240612",
                bd(24), "KG", referenceDate.minusDays(4), "MP - Matières premières", false));
        batches.add(new BatchRecord(conserve.code(), conserve.commercialName(), "B-240801",
                bd(6), "KG", referenceDate.plusDays(44), "MP - Matières premières", false));
        batches.add(new BatchRecord("DISH-1L", "Liquide vaisselle Citron 1 L", "PF-240915-A",
                bd(120), "PCE", referenceDate.plusDays(55), "PF - Produits finis", true));
        batches.add(new BatchRecord("BLEACH-5L", "Eau de Javel 12° 5 L", "PF-240820-B",
                bd(75), "PCE", referenceDate.plusDays(38), "PF - Produits finis", true));
        batches.add(new BatchRecord("SOAP-500", "Savon liquide mains 500 ml", "PF-240701-C",
                bd(40), "PCE", referenceDate.plusDays(19), "PF - Produits finis", true));
        batches.add(new BatchRecord("LAUND-2L", "Lessive liquide Fleur 2 L", "PF-240910-D",
                bd(160), "PCE", referenceDate.plusDays(210), "PF - Produits finis", true));
        batches.add(new BatchRecord("DISINF-1L", "Désinfectant surfaces 1 L", "PF-240501-E",
                bd(9), "PCE", referenceDate.minusDays(11), "PF - Produits finis", true));
    }

    private void seedSales() {
        Random random = new Random(2024_06_15);
        String[] reps = {"Amine Cherifi", "Souad Merabet", "Bilal Ouali", "Amine Cherifi", "Hakim Djellali"};
        LocalDate today = referenceDate;
        LocalDate start = today.minusMonths(17).withDayOfMonth(1);

        for (LocalDate day = start; !day.isAfter(today); day = day.plusDays(1)) {
            // Friday is the weekly rest day in the region: very few deliveries.
            if (day.getDayOfWeek().getValue() == 5) {
                continue;
            }
            int seasonality = (day.getMonthValue() >= 5 && day.getMonthValue() <= 9) ? 2 : 0;
            int invoiceCount = 2 + random.nextInt(4) + seasonality;
            for (int invoice = 0; invoice < invoiceCount; invoice++) {
                CustomerRecord customer = customers.get(random.nextInt(customers.size()));
                String rep = reps[random.nextInt(reps.length)];
                int lines = 1 + random.nextInt(3);
                for (int line = 0; line < lines; line++) {
                    Product product = products.get(random.nextInt(products.size()));
                    if (product.type() == ProductType.RAW_MATERIAL) {
                        continue;
                    }
                    int baseQty = switch (product.packaging()) {
                        case "Fût 20 L" -> 1 + random.nextInt(6);
                        case "Bidon 5 L" -> 2 + random.nextInt(20);
                        case "Sac 25 kg" -> 1 + random.nextInt(8);
                        case "Carton 60 pcs", "Sac 1000 pcs" -> 1 + random.nextInt(10);
                        default -> 4 + random.nextInt(48);
                    };
                    boolean wholesale = "GROSSISTE".equals(customer.category())
                            || "GRANDE_SURFACE".equals(customer.category())
                            || random.nextBoolean();
                    BigDecimal unitPrice = wholesale ? product.wholesalePrice() : product.retailPrice();
                    boolean paid = !wholesale || random.nextInt(100) < 62;
                    sales.add(new SalesRecord(day, product.id(), product.sku(),
                            product.displayName(false), product.category(), BigDecimal.valueOf(baseQty),
                            unitPrice, product.productionCost(), product.vatRate(), customer.id(),
                            customer.name(), rep, paid));
                }
            }
        }
        // Guarantee activity for the current day so the "sales today" KPI is never empty.
        if (sales.stream().noneMatch(s -> s.date().equals(today))) {
            Product product = products.get(1);
            sales.add(new SalesRecord(today, product.id(), product.sku(), product.displayName(false),
                    product.category(), BigDecimal.valueOf(36), product.wholesalePrice(),
                    product.productionCost(), product.vatRate(), customers.get(0).id(),
                    customers.get(0).name(), reps[0], false));
        }
    }

    private void seedPurchases() {
        Random random = new Random(99);
        String[] categories = {"CHEMICAL", "SURFACTANT", "FRAGRANCE", "PACKAGING", "COLORANT", "ADDITIVE", "SOLVENT"};
        LocalDate start = referenceDate.minusMonths(17).withDayOfMonth(1);
        for (LocalDate day = start; !day.isAfter(referenceDate); day = day.plusDays(1)) {
            if (random.nextInt(100) > 22) {
                continue;
            }
            SupplierRecord supplier = suppliers.get(random.nextInt(suppliers.size()));
            BigDecimal amount = bd(45_000 + random.nextInt(620_000));
            BigDecimal vat = amount.multiply(BigDecimal.valueOf(0.19)).setScale(2, RoundingMode.HALF_UP);
            purchases.add(new PurchaseRecord(day, supplier.id(), supplier.name(),
                    categories[random.nextInt(categories.length)], amount, vat, random.nextBoolean()));
        }
    }

    private void seedExpenses() {
        Random random = new Random(1234);
        LocalDate start = referenceDate.minusMonths(17).withDayOfMonth(1);
        for (LocalDate month = start; !month.isAfter(referenceDate); month = month.plusMonths(1)) {
            LocalDate inMonth = month.withDayOfMonth(Math.min(5, month.lengthOfMonth()));
            expenses.add(new ExpenseRecord(inMonth, "RENT", "Loyer entrepôt & bureaux", bd(180_000), true, "BANK"));
            expenses.add(new ExpenseRecord(inMonth.plusDays(2), "SALARIES", "Salaires du personnel", bd(1_240_000), true, "BANK"));
            expenses.add(new ExpenseRecord(inMonth.plusDays(3), "UTILITIES", "Électricité & gaz (Sonelgaz)", bd(96_000 + random.nextInt(40_000)), true, "BANK"));
            expenses.add(new ExpenseRecord(inMonth.plusDays(4), "UTILITIES", "Eau (ADE)", bd(18_000 + random.nextInt(8_000)), true, "BANK"));
            expenses.add(new ExpenseRecord(inMonth.plusDays(6), "VEHICLE", "Carburant véhicules de livraison", bd(120_000 + random.nextInt(90_000)), true, "CASH"));
            expenses.add(new ExpenseRecord(inMonth.plusDays(8), "MAINTENANCE", "Maintenance lignes de production", bd(45_000 + random.nextInt(120_000)), random.nextBoolean(), "CASH"));
            expenses.add(new ExpenseRecord(inMonth.plusDays(10), "ADMIN", "Fournitures administratives", bd(12_000 + random.nextInt(18_000)), true, "CASH"));
            expenses.add(new ExpenseRecord(inMonth.plusDays(12), "LOGISTICS", "Transport et logistique", bd(60_000 + random.nextInt(80_000)), random.nextBoolean(), "BANK"));
        }
    }

    private void seedProduction() {
        Random random = new Random(555);
        String[] statuses = {"COMPLETED", "COMPLETED", "COMPLETED", "COMPLETED", "QUALITY_CONTROL",
                "IN_PROGRESS", "APPROVED", "WAITING_MATERIALS", "DRAFT"};
        LocalDate start = referenceDate.minusMonths(17).withDayOfMonth(1);
        int sequence = 1;
        for (LocalDate day = start; !day.isAfter(referenceDate); day = day.plusDays(1)) {
            if (random.nextInt(100) > 32) {
                continue;
            }
            Product product = products.get(random.nextInt(products.size()));
            int planned = switch (product.packaging()) {
                case "Fût 20 L" -> 20 + random.nextInt(60);
                case "Bidon 5 L" -> 80 + random.nextInt(300);
                case "Sac 25 kg" -> 20 + random.nextInt(60);
                default -> 200 + random.nextInt(900);
            };
            String status = day.equals(referenceDate)
                    ? statuses[4 + random.nextInt(statuses.length - 4)]
                    : statuses[random.nextInt(5)];
            BigDecimal wastePercent = BigDecimal.valueOf(0.5 + random.nextDouble() * 2.5);
            int actual = (int) Math.round(planned * (100 - wastePercent.doubleValue()) / 100);
            BigDecimal materialCost = product.productionCost()
                    .multiply(BigDecimal.valueOf(actual)).multiply(BigDecimal.valueOf(0.82))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal packagingCost = product.productionCost()
                    .multiply(BigDecimal.valueOf(actual)).multiply(BigDecimal.valueOf(0.11))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal otherCost = product.productionCost()
                    .multiply(BigDecimal.valueOf(actual)).multiply(BigDecimal.valueOf(0.07))
                    .setScale(2, RoundingMode.HALF_UP);
            production.add(new ProductionRecord(day, "OF-" + day.getYear() + "-" + String.format("%04d", sequence++),
                    product.id(), product.displayName(false), BigDecimal.valueOf(planned),
                    BigDecimal.valueOf(actual), BigDecimal.valueOf(planned - actual), status,
                    materialCost, packagingCost, otherCost));
        }
    }

    private void seedActivities() {
        activities.add(new ActivityEntry(1, java.time.Instant.now().minusSeconds(420), "Amine Cherifi",
                "Invoice FA-2024-0912 validated — 68 400,00 DZD", "SALES", "FA-2024-0912", Severity.INFO));
        activities.add(new ActivityEntry(2, java.time.Instant.now().minusSeconds(1_500), "Karim Ferhat",
                "Production order OF-2024-0418 moved to Quality Control", "PRODUCTION", "OF-2024-0418", Severity.INFO));
        activities.add(new ActivityEntry(3, java.time.Instant.now().minusSeconds(3_200), "Nadia Bounouh",
                "Stock adjustment +120 PCE on DISH-1L (inventory count)", "INVENTORY", "AJ-2024-0117", Severity.WARNING));
        activities.add(new ActivityEntry(4, java.time.Instant.now().minusSeconds(5_400), "Farida Ziani",
                "Supplier payment 320 000,00 DZD to Chimie Algérie SPA", "FINANCE", "PAY-2024-0208", Severity.INFO));
        activities.add(new ActivityEntry(5, java.time.Instant.now().minusSeconds(7_200), "Yacine Belkacem",
                "Formulation F-JAVEL-5L v3 approved by production manager", "FORMULATIONS", "F-JAVEL-5L", Severity.INFO));
        activities.add(new ActivityEntry(6, java.time.Instant.now().minusSeconds(9_100), "Salima Haddad",
                "User session expired — re-authentication required", "SECURITY", "admin", Severity.WARNING));
        activities.add(new ActivityEntry(7, java.time.Instant.now().minusSeconds(12_600), "Nadia Bounouh",
                "Batch B-240915 flagged for expiry (12 days remaining)", "SAFETY", "B-240915", Severity.CRITICAL));
        activities.add(new ActivityEntry(8, java.time.Instant.now().minusSeconds(15_800), "Amine Cherifi",
                "Delivery note BL-2024-0731 signed by customer SARL Nour Distribution", "DISTRIBUTION", "BL-2024-0731", Severity.INFO));
        activities.add(new ActivityEntry(9, java.time.Instant.now().minusSeconds(20_000), "Karim Ferhat",
                "Raw material consumption recorded for OF-2024-0411", "PRODUCTION", "OF-2024-0411", Severity.INFO));
        activities.add(new ActivityEntry(10, java.time.Instant.now().minusSeconds(26_000), "Farida Ziani",
                "Cash closing 2024-10-31 balanced at 486 250,00 DZD", "FINANCE", "CAISSE-1031", Severity.INFO));
        activities.add(new ActivityEntry(11, java.time.Instant.now().minusSeconds(31_000), "Yacine Belkacem",
                "Role SALES_MANAGER permissions updated", "USERS", "ROLE-07", Severity.WARNING));
        activities.add(new ActivityEntry(12, java.time.Instant.now().minusSeconds(40_000), "Souad Merabet",
                "Quotation DEV-2024-0155 accepted by Groupe Djurdjura Hygiène", "SALES", "DEV-2024-0155", Severity.INFO));
    }

    public List<NotificationItem> notifications() {
        List<NotificationItem> list = new ArrayList<>();
        long id = 1;
        for (Product product : products) {
            if (product.isOutOfStock()) {
                list.add(new NotificationItem(id++, NotificationItem.Category.STOCK, Severity.CRITICAL,
                        "Rupture de stock : " + product.displayName(false),
                        product.sku() + " — stock 0 " + product.unit() + " (seuil " + product.minStock() + ")",
                        java.time.Instant.now().minusSeconds(900), "products", false));
            } else if (product.isLowStock()) {
                list.add(new NotificationItem(id++, NotificationItem.Category.STOCK, Severity.WARNING,
                        "Stock bas : " + product.displayName(false),
                        product.sku() + " — " + product.stockOnHand() + " " + product.unit()
                                + " restants (seuil " + product.minStock() + ")",
                        java.time.Instant.now().minusSeconds(3_600), "products", false));
            }
        }
        for (RawMaterial material : rawMaterials) {
            if (material.isLowStock()) {
                list.add(new NotificationItem(id++, NotificationItem.Category.STOCK, Severity.WARNING,
                        "Matière première à réapprovisionner : " + material.commercialName(),
                        material.code() + " — " + material.stock().stripTrailingZeros().toPlainString()
                                + " " + material.storageUnit() + " (seuil " + material.minStock().stripTrailingZeros().toPlainString() + ")",
                        java.time.Instant.now().minusSeconds(7_200), "raw-materials", false));
            }
            if (material.isExpiringWithin(45, referenceDate)) {
                list.add(new NotificationItem(id++, NotificationItem.Category.EXPIRY, Severity.CRITICAL,
                        "Lot proche de la péremption : " + material.commercialName(),
                        "Lot " + material.batchNumber() + " — expire le "
                                + com.chemmanager.util.Formatters.shortDate(material.expirationDate()),
                        java.time.Instant.now().minusSeconds(10_800), "raw-materials", false));
            }
            if (!material.sdsAvailable()) {
                list.add(new NotificationItem(id++, NotificationItem.Category.SAFETY, Severity.WARNING,
                        "FDS manquante : " + material.commercialName(),
                        material.code() + " — ajoutez la fiche de données de sécurité du fournisseur",
                        java.time.Instant.now().minusSeconds(43_200), "safety", false));
            }
        }
        for (BatchRecord batch : batches) {
            if (batch.expiryDate() != null && batch.expiryDate().isBefore(referenceDate.plusDays(30))) {
                list.add(new NotificationItem(id++, NotificationItem.Category.EXPIRY, Severity.CRITICAL,
                        "Lot " + batch.batchNumber() + " à écouler en priorité (FEFO)",
                        batch.itemName() + " — " + batch.quantity().stripTrailingZeros().toPlainString() + " "
                                + batch.unit() + " — péremption "
                                + com.chemmanager.util.Formatters.shortDate(batch.expiryDate()),
                        java.time.Instant.now().minusSeconds(14_400), "inventory", false));
            }
        }
        for (ProductionRecord order : production) {
            if (order.isOpen() && "QUALITY_CONTROL".equals(order.status())) {
                list.add(new NotificationItem(id++, NotificationItem.Category.QUALITY, Severity.WARNING,
                        "Contrôle qualité en attente : " + order.orderNumber(),
                        order.productName() + " — " + order.actualQty().stripTrailingZeros().toPlainString() + " unités",
                        java.time.Instant.now().minusSeconds(18_000), "production", false));
            }
        }
        for (CustomerRecord customer : customers) {
            if (customer.balance().compareTo(BigDecimal.valueOf(250_000)) > 0) {
                list.add(new NotificationItem(id++, NotificationItem.Category.PAYMENT, Severity.WARNING,
                        "Créance client élevée : " + customer.name(),
                        com.chemmanager.util.Formatters.currency(customer.balance()) + " impayés — limite "
                                + com.chemmanager.util.Formatters.currency(customer.creditLimit()),
                        java.time.Instant.now().minusSeconds(21_600), "customers", false));
            }
        }
        list.add(new NotificationItem(id++, NotificationItem.Category.SYSTEM, Severity.INFO,
                "Sauvegarde base de données planifiée",
                "Prochaine sauvegarde automatique à 02:00 — dossier /var/backups/chemmanager",
                java.time.Instant.now().minusSeconds(25_200), "settings", true));
        list.add(new NotificationItem(id, NotificationItem.Category.DELIVERY, Severity.WARNING,
                "Livraison en retard : BL-2024-0728",
                "Tournée Sétif-Est — chauffeur Ali B. — prévue hier",
                java.time.Instant.now().minusSeconds(28_800), "distribution", false));
        list.sort((a, b) -> b.createdAt().compareTo(a.createdAt()));
        return list;
    }

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
