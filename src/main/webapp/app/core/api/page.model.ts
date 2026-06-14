/**
 * Spring Data `Page<T>` JSON envelope as serialized by the backend (classic
 * `PageImpl` shape — Spring Boot 3.4 default, no `serialization-mode` override).
 * The same endpoints also emit `X-Total-Count`/`Link` headers, but the body is
 * richer so we read pagination from here.
 */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  /** Current page index (0-based). */
  number: number;
  size: number;
  numberOfElements: number;
  first: boolean;
  last: boolean;
  empty: boolean;
  sort?: unknown;
  pageable?: unknown;
}

export type SortDirection = 'asc' | 'desc';

/**
 * Query params accepted by paged endpoints (Spring `Pageable`). `page` is
 * 0-based; `sort` entries are `property,direction` strings (e.g. `name,asc`).
 */
export interface PageRequest {
  page?: number;
  size?: number;
  sort?: string[];
}

export const DEFAULT_PAGE_SIZE = 25;

export const defaultPageRequest = (): PageRequest => ({ page: 0, size: DEFAULT_PAGE_SIZE, sort: [] });

/** Build a Spring sort param, e.g. `toSortParam('name', 'desc') → 'name,desc'`. */
export const toSortParam = (property: string, direction: SortDirection = 'asc'): string => `${property},${direction}`;

export const emptyPage = <T>(): Page<T> => ({
  content: [],
  totalElements: 0,
  totalPages: 0,
  number: 0,
  size: 0,
  numberOfElements: 0,
  first: true,
  last: true,
  empty: true,
});
