import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { FormsModule, NgForm } from '@angular/forms';
import { AccountComponent } from './account.component';

export interface Product {
  id: string;
  name: string;
  category: string;
  price: number;
  compareAt: number | null;
  image: string;
  color: string;
  badge: string | null;
  description: string;
  featured: boolean;
}

interface OrderReceipt {
  id: string;
  status: string;
  subtotal: number;
  shipping: number;
  total: number;
  itemCount: number;
}

interface CartLine { product: Product; quantity: number; }
type Cart = Record<string, number>;

function readLocal<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key);
    return raw ? JSON.parse(raw) as T : fallback;
  } catch {
    return fallback;
  }
}

@Component({
  selector: 'app-root',
  imports: [FormsModule, AccountComponent],
  templateUrl: './app.component.html'
})
export class AppComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private toastTimer: ReturnType<typeof setTimeout> | undefined;
  readonly shippingMinimum = 35;
  readonly shippingCharge = 4.95;
  readonly categories = [
    { label: 'Everything ✦', value: 'All' },
    { label: 'Keychains', value: 'Keychains' },
    { label: 'Phone charms', value: 'Phone Charms' },
    { label: 'Accessories', value: 'Accessories' },
    { label: 'Stickers', value: 'Stickers' }
  ];
  readonly products = signal<Product[]>([]);
  readonly loading = signal(true);
  readonly loadError = signal(false);
  readonly category = signal('All');
  readonly search = signal('');
  readonly sort = signal('featured');
  readonly cart = signal<Cart>(readLocal<Cart>('dudu-bubu-cart', {}));
  readonly favorites = signal<string[]>(readLocal<string[]>('dudu-bubu-favorites', []));
  readonly isCartOpen = signal(false);
  readonly isCheckoutOpen = signal(false);
  readonly submitting = signal(false);
  readonly checkoutError = signal('');
  readonly receipt = signal<OrderReceipt | null>(null);
  readonly toast = signal('');

  readonly filtered = computed(() => {
    const q = this.search().trim().toLowerCase();
    const list = this.products().filter(p =>
      (this.category() === 'All' || p.category === this.category()) &&
      `${p.name} ${p.description} ${p.category}`.toLowerCase().includes(q)
    );
    const sort = this.sort();
    if (sort === 'price-asc') list.sort((a, b) => a.price - b.price);
    else if (sort === 'price-desc') list.sort((a, b) => b.price - a.price);
    else if (sort === 'name') list.sort((a, b) => a.name.localeCompare(b.name));
    else list.sort((a, b) => Number(b.featured) - Number(a.featured));
    return list;
  });
  readonly cartLines = computed<CartLine[]>(() =>
    Object.entries(this.cart()).map(([id, quantity]) => ({
      product: this.products().find(p => p.id === id), quantity
    })).filter((x): x is CartLine => !!x.product && Number.isInteger(x.quantity) && x.quantity > 0)
  );
  readonly cartCount = computed(() => this.cartLines().reduce((sum, x) => sum + x.quantity, 0));
  readonly subtotal = computed(() => this.cartLines().reduce((sum, x) => sum + x.product.price * x.quantity, 0));
  readonly shipping = computed(() => this.subtotal() >= this.shippingMinimum ? 0 : this.shippingCharge);
  readonly freeShippingRemaining = computed(() => Math.max(0, this.shippingMinimum - this.subtotal()));
  readonly freeShippingPercent = computed(() => Math.min(100, this.subtotal() / this.shippingMinimum * 100));

  ngOnInit(): void {
    this.http.get<Product[]>('/api/products').subscribe({
      next: products => {
        this.products.set(products);
        this.cart.update(saved => Object.fromEntries(
          Object.entries(saved).filter(([id, quantity]) => products.some(p => p.id === id) && Number.isInteger(quantity) && quantity > 0 && quantity <= 10)
        ));
        this.persistCart();
        this.loading.set(false);
      },
      error: () => { this.loading.set(false); this.loadError.set(true); }
    });
  }

  money(value: number): string { return `$${Number(value).toFixed(2)}`; }
  setCategory(value: string): void { this.category.set(value); }
  setSearch(event: Event): void { this.search.set((event.target as HTMLInputElement).value); }
  setSort(event: Event): void { this.sort.set((event.target as HTMLSelectElement).value); }
  resetFilters(): void { this.search.set(''); this.category.set('All'); }
  isFavorite(id: string): boolean { return this.favorites().includes(id); }

  toggleFavorite(id: string): void {
    this.favorites.update(prev => prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id]);
    localStorage.setItem('dudu-bubu-favorites', JSON.stringify(this.favorites()));
    this.notify(this.isFavorite(id) ? 'Saved to your favorites ♡' : 'Removed from favorites');
  }

  addItem(id: string): void {
    if (!this.products().some(p => p.id === id)) return;
    if ((this.cart()[id] ?? 0) >= 10) { this.notify('Maximum 10 of each item per order.'); return; }
    this.cart.update(prev => ({ ...prev, [id]: (prev[id] ?? 0) + 1 }));
    this.persistCart();
    this.notify('Added to your happy little cart ♡');
  }

  changeQuantity(id: string, amount: number): void {
    const next = (this.cart()[id] ?? 0) + amount;
    if (next > 10) { this.notify('Maximum 10 of each item per order.'); return; }
    this.cart.update(prev => {
      const result = { ...prev };
      if (next <= 0) delete result[id]; else result[id] = next;
      return result;
    });
    this.persistCart();
  }
  removeItem(id: string): void { this.cart.update(prev => { const next = { ...prev }; delete next[id]; return next; }); this.persistCart(); }
  openCart(): void { this.isCartOpen.set(true); }
  closeCart(): void { this.isCartOpen.set(false); }
  openCheckout(): void {
    if (!this.cartCount()) return;
    this.closeCart();
    this.checkoutError.set('');
    this.receipt.set(null);
    this.isCheckoutOpen.set(true);
  }
  closeCheckout(): void { this.isCheckoutOpen.set(false); }

  placeOrder(form: NgForm): void {
    if (form.invalid || !this.cartCount() || this.submitting()) { form.control.markAllAsTouched(); return; }
    this.submitting.set(true);
    this.checkoutError.set('');
    const items = this.cartLines().map(({ product, quantity }) => ({ id: product.id, quantity }));
    this.http.post<OrderReceipt>('/api/orders', { customer: form.value, items }).subscribe({
      next: receipt => {
        this.receipt.set(receipt);
        this.cart.set({});
        this.persistCart();
        this.submitting.set(false);
        form.resetForm();
      },
      error: (err: HttpErrorResponse) => {
        const message: unknown = err.error?.error;
        this.checkoutError.set(typeof message === 'string' ? message : 'Order could not be saved. Check the API and try again.');
        this.submitting.set(false);
      }
    });
  }

  notify(message: string): void {
    this.toast.set(message);
    if (this.toastTimer) clearTimeout(this.toastTimer);
    this.toastTimer = setTimeout(() => this.toast.set(''), 2200);
  }
  private persistCart(): void { localStorage.setItem('dudu-bubu-cart', JSON.stringify(this.cart())); }
}
