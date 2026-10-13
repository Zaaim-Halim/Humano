import { Injectable } from '@angular/core';

import { RestResourceService } from 'app/core/api';

import { Project } from '../models/project.model';

/** Projects people log time against — `/api/hr/projects` (read access for all employees). */
@Injectable({ providedIn: 'root' })
export class ProjectService extends RestResourceService<Project, Project, Partial<Project>, Partial<Project>> {
  constructor() {
    super('api/hr/projects');
  }
}
