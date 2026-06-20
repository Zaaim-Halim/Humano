/** `GET /api/payroll/currencies` — reference list (backend `CurrencyView`). */
export interface Currency {
  id: string;
  code: string;
  name: string;
  symbol: string;
}
