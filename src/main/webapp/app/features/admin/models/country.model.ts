/** `GET /api/hr/countries` — reference list (backend `CountryView`). */
export interface Country {
  id: string;
  /** ISO 3166-1 alpha-2, e.g. `ES`. */
  code: string;
  name: string;
}
