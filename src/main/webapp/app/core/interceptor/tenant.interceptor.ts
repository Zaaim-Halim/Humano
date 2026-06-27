import { Injectable, inject } from '@angular/core';
import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Observable } from 'rxjs';

import { TenantContextService } from 'app/core/tenant/tenant-context.service';

/** Matches this app's backend endpoints (relative URLs like `api/...`, `services/...`, `management/...`). */
const BACKEND_URL = /(^|\/)(api|services|management|v3\/api-docs)(\/|$)/;

/**
 * Attaches the chosen organization as the `X-Tenant-ID` header on backend requests so the server can
 * resolve the tenant (interim until production subdomain-based resolution). No-op when no tenant is set
 * (platform/master context) or for non-backend URLs (e.g. i18n assets).
 */
@Injectable()
export class TenantInterceptor implements HttpInterceptor {
  private readonly tenantContext = inject(TenantContextService);

  intercept(request: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    const tenantId = this.tenantContext.tenantId();
    if (tenantId && BACKEND_URL.test(request.url)) {
      request = request.clone({ setHeaders: { 'X-Tenant-ID': tenantId } });
    }
    return next.handle(request);
  }
}
