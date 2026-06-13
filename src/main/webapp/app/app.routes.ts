import { Routes } from '@angular/router';

// Feature routes are (re)introduced per persona surface during the rebuild.
// `/dev/ui` is the design-system gallery / verification harness.
const routes: Routes = [
  {
    path: 'dev/ui',
    title: 'UI gallery',
    loadComponent: () => import('./dev/ui-gallery.component'),
  },
];

export default routes;
