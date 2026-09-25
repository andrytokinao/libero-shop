import {
  CashRemittance,
  Category,
  DeliveryStatus,
  Invoice,
  MovementType,
  Payment,
  PaymentMethod,
  PaymentStatus,
  Product,
  RemittanceStatus,
  RoleApp,
  Sale,
  SaleLine,
  StockMovement,
  StockOutput,
  Supplier,
  Supply,
  UserApp,
  calculateTotal,
} from '../models';
import { atToday, daysAgoAt } from '../utils/date.util';

/** Every in-memory collection of the demo, plus the id sequences that extend them. */
export interface MockDatabase {
  users: UserApp[];
  categories: Category[];
  products: Product[];
  suppliers: Supplier[];
  sales: Sale[];
  invoices: Invoice[];
  payments: Payment[];
  remittances: CashRemittance[];
  movements: StockMovement[];
  sequences: {
    sale: number;
    saleLine: number;
    invoice: number;
    invoiceNumber: number;
    payment: number;
    remittance: number;
    movement: number;
  };
}

/** Builds a fresh, fully wired dataset — object identity is shared across collections. */
export function buildMockDatabase(): MockDatabase {
  // ---------------------------------------------------------------- users
  const fatima: UserApp = { id: 1, fullName: 'Fatima Randria', role: RoleApp.CASHIER };
  const hary: UserApp = { id: 2, fullName: 'Hary Rakoto', role: RoleApp.CASHIER };
  const joseph: UserApp = { id: 3, fullName: 'Joseph Andrianina', role: RoleApp.DEPOT_AGENT };
  const nadia: UserApp = { id: 4, fullName: 'Nadia Rasolofo', role: RoleApp.DEPOT_MANAGER };
  const mparany: UserApp = { id: 5, fullName: 'Mparany Solofo', role: RoleApp.SUPER_ADMIN };
  const users = [fatima, hary, joseph, nadia, mparany];

  // ----------------------------------------------------------- categories
  const epicerie: Category = { id: 1, name: 'Épicerie' };
  const boissons: Category = { id: 2, name: 'Boissons' };
  const hygiene: Category = { id: 3, name: 'Hygiène' };
  const laitiers: Category = { id: 4, name: 'Produits laitiers' };
  const categories = [epicerie, boissons, hygiene, laitiers];

  // ------------------------------------------------------------- products
  const products: Product[] = [
    { id: 1, name: 'Riz 5kg', price: 12500, stockQuantity: 40, barcode: '6001001000015', category: epicerie },
    { id: 2, name: 'Huile 1L', price: 6800, stockQuantity: 25, barcode: '6001001000022', category: epicerie },
    { id: 3, name: 'Sucre 1kg', price: 3200, stockQuantity: 8, barcode: '6001001000039', category: epicerie },
    { id: 4, name: 'Farine 1kg', price: 2800, stockQuantity: 60, barcode: '6001001000046', category: epicerie },
    { id: 5, name: 'Lait en poudre 400g', price: 9500, stockQuantity: 15, barcode: '6001002000053', category: laitiers },
    { id: 6, name: 'Savon de Marseille', price: 1500, stockQuantity: 100, barcode: '6001003000060', category: hygiene },
    { id: 7, name: 'Eau minérale 1.5L', price: 1200, stockQuantity: 6, barcode: '6001004000077', category: boissons },
    { id: 8, name: 'Pâtes 500g', price: 2100, stockQuantity: 50, barcode: '6001001000084', category: epicerie },
    { id: 9, name: 'Café moulu 250g', price: 7400, stockQuantity: 18, barcode: '6001001000091', category: epicerie },
    { id: 10, name: 'Yaourt nature 4×125g', price: 5200, stockQuantity: 9, barcode: '6001002000107', category: laitiers },
    { id: 11, name: 'Dentifrice 75ml', price: 4300, stockQuantity: 32, barcode: '6001003000114', category: hygiene },
    { id: 12, name: "Jus d'orange 1L", price: 4800, stockQuantity: 4, barcode: '6001004000121', category: boissons },
  ];
  const product = (id: number): Product => products.find((p) => p.id === id)!;

  // ------------------------------------------------------------ suppliers
  const grossiste: Supplier = {
    id: 1,
    name: 'Grossiste Analakely',
    contact: '034 12 345 67',
    suppliedProducts: 'Riz, Sucre, Farine, Pâtes',
  };
  const distriLait: Supplier = {
    id: 2,
    name: 'Distri Lait SARL',
    contact: '032 55 987 21',
    suppliedProducts: 'Lait en poudre, yaourts, beurre',
  };
  const aquaPlus: Supplier = {
    id: 3,
    name: 'Aqua Plus',
    contact: '033 44 221 09',
    suppliedProducts: 'Eau minérale, jus, boissons gazeuses',
  };
  const hygieneMada: Supplier = {
    id: 4,
    name: 'Hygiène Mada',
    contact: '038 77 654 32',
    suppliedProducts: 'Savons, dentifrices, détergents',
  };
  const suppliers = [grossiste, distriLait, aquaPlus, hygieneMada];

  // ---------------------------------------------------------------- sales
  let saleLineSeq = 1;
  const line = (productId: number, quantity: number): SaleLine => {
    const p = product(productId);
    return { id: saleLineSeq++, quantity, unitPrice: p.price, product: p };
  };

  const makeSale = (
    id: number,
    seller: UserApp,
    saleDate: string,
    paymentStatus: PaymentStatus,
    lines: SaleLine[],
  ): Sale => ({ id, saleDate, paymentStatus, totalAmount: calculateTotal(lines), seller, lines });

  const sale1027 = makeSale(1027, fatima, daysAgoAt(1, 15, 30), PaymentStatus.PAID, [line(2, 3)]);
  const sale1028 = makeSale(1028, hary, daysAgoAt(1, 16, 5), PaymentStatus.PAID, [line(1, 1), line(10, 2)]);
  const sale1029 = makeSale(1029, fatima, atToday(8, 40), PaymentStatus.PAID, [line(1, 2), line(2, 1)]);
  const sale1030 = makeSale(1030, hary, atToday(9, 15), PaymentStatus.UNPAID, [line(5, 1), line(8, 3)]);
  const sale1031 = makeSale(1031, fatima, atToday(10, 2), PaymentStatus.PAID, [line(6, 4), line(7, 2)]);
  const sale1032 = makeSale(1032, hary, atToday(11, 20), PaymentStatus.PAID, [line(9, 2), line(11, 1)]);
  const sale1033 = makeSale(1033, fatima, atToday(12, 5), PaymentStatus.UNPAID, [line(4, 5)]);
  const sale1034 = makeSale(1034, hary, atToday(9, 50), PaymentStatus.PAID, [line(3, 2)]);
  const sale1035 = makeSale(1035, fatima, atToday(8, 10), PaymentStatus.PAID, [line(12, 2)]);

  const sales = [
    sale1035, sale1034, sale1033, sale1032, sale1031, sale1030, sale1029, sale1028, sale1027,
  ];

  // ------------------------------------------------------------- invoices
  const makeInvoice = (
    sale: Sale,
    clientName: string,
    deliveryStatus: DeliveryStatus,
    printed = false,
  ): Invoice => ({
    id: sale.id,
    invoiceNumber: `F-${sale.id}`,
    invoiceDate: sale.saleDate,
    clientName,
    paymentStatus: sale.paymentStatus,
    deliveryStatus,
    printed,
    sale,
  });

  const inv1027 = makeInvoice(sale1027, 'Hotely Vaha', DeliveryStatus.DELIVERED, true);
  const inv1028 = makeInvoice(sale1028, 'Tiana Rabe', DeliveryStatus.DELIVERED, true);
  const inv1029 = makeInvoice(sale1029, 'Rina Hasina', DeliveryStatus.DELIVERED, true);
  const inv1030 = makeInvoice(sale1030, 'Tovo Rakotondrazaka', DeliveryStatus.PENDING);
  const inv1031 = makeInvoice(sale1031, 'Client comptoir', DeliveryStatus.PENDING);
  const inv1032 = makeInvoice(sale1032, 'Miora Ranaivo', DeliveryStatus.DELIVERED);
  const inv1033 = makeInvoice(sale1033, 'Épicerie Ambohipo', DeliveryStatus.PENDING);
  const inv1034 = makeInvoice(sale1034, 'Solo Andriamana', DeliveryStatus.DELIVERED);
  const inv1035 = makeInvoice(sale1035, 'Gargote Anosy', DeliveryStatus.DELIVERED);

  const invoices = [
    inv1035, inv1034, inv1033, inv1032, inv1031, inv1030, inv1029, inv1028, inv1027,
  ];

  // ---------------------------------------------------- cash remittances
  // Joseph brought yesterday's depot cash to the cash desk; Fatima confirmed it.
  const remittance1: CashRemittance = {
    id: 1,
    amount: sale1027.totalAmount,
    remittanceDate: daysAgoAt(1, 17, 0),
    status: RemittanceStatus.CONFIRMED,
    submittedBy: joseph,
    confirmedBy: fatima,
  };
  // Submitted this morning, still waiting for the cash desk to acknowledge it.
  const remittance2: CashRemittance = {
    id: 2,
    amount: sale1035.totalAmount,
    remittanceDate: atToday(11, 0),
    status: RemittanceStatus.PENDING,
    submittedBy: joseph,
    confirmedBy: null,
  };
  const remittances = [remittance2, remittance1];

  // ------------------------------------------------------------- payments
  let paymentSeq = 1;
  const makePayment = (
    invoice: Invoice,
    paymentMethod: PaymentMethod,
    collectedBy: UserApp,
    paymentDate: string,
    cashRemittance: CashRemittance | null = null,
  ): Payment => ({
    id: paymentSeq++,
    amount: invoice.sale.totalAmount,
    paymentMethod,
    paymentDate,
    invoice,
    collectedBy,
    cashRemittance,
  });

  const payments: Payment[] = [
    // Cash-desk collections: no remittance to make, the money is already there.
    makePayment(inv1028, PaymentMethod.CASH, hary, daysAgoAt(1, 16, 5)),
    makePayment(inv1029, PaymentMethod.CASH, fatima, atToday(8, 40)),
    makePayment(inv1031, PaymentMethod.MOBILE_MONEY, fatima, atToday(10, 2)),
    makePayment(inv1032, PaymentMethod.CASH, hary, atToday(11, 20)),
    // Depot collections on delivery of unpaid orders.
    makePayment(inv1027, PaymentMethod.CASH, joseph, daysAgoAt(1, 16, 40), remittance1),
    makePayment(inv1035, PaymentMethod.CASH, joseph, atToday(10, 30), remittance2),
    makePayment(inv1034, PaymentMethod.CASH, joseph, atToday(11, 45)), // still in Joseph's hands
  ];

  // ------------------------------------------------------ stock movements
  let movementSeq = 1;
  const makeSupply = (
    productId: number,
    quantity: number,
    supplier: Supplier,
    movementDate: string,
  ): Supply => ({
    id: movementSeq++,
    movementType: MovementType.SUPPLY,
    quantity,
    movementDate,
    product: product(productId),
    performedBy: nadia,
    supplier,
  });

  const movements: StockMovement[] = [
    makeSupply(1, 20, grossiste, daysAgoAt(1, 7, 30)),
    makeSupply(4, 30, grossiste, daysAgoAt(2, 8, 15)),
    makeSupply(5, 10, distriLait, daysAgoAt(3, 9, 0)),
    makeSupply(7, 12, aquaPlus, daysAgoAt(4, 7, 45)),
    makeSupply(11, 24, hygieneMada, daysAgoAt(5, 10, 20)),
  ];

  // One OUTPUT movement per sold line, recorded when the sale leaves the depot.
  for (const invoice of [...invoices].reverse()) {
    for (const saleLine of invoice.sale.lines) {
      const output: StockOutput = {
        id: movementSeq++,
        movementType: MovementType.OUTPUT,
        quantity: saleLine.quantity,
        movementDate: invoice.invoiceDate,
        product: saleLine.product,
        performedBy: invoice.sale.seller,
        invoice,
      };
      movements.push(output);
    }
  }
  movements.sort((a, b) => b.movementDate.localeCompare(a.movementDate));

  return {
    users,
    categories,
    products,
    suppliers,
    sales,
    invoices,
    payments,
    remittances,
    movements,
    sequences: {
      sale: 1036,
      saleLine: saleLineSeq,
      invoice: 1036,
      invoiceNumber: 1036,
      payment: paymentSeq,
      remittance: 3,
      movement: movementSeq,
    },
  };
}
