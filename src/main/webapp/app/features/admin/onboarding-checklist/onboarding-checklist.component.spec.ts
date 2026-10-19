import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';

import { OnboardingChecklist } from '../models/onboarding.model';
import { OnboardingService } from '../services/onboarding.service';
import OnboardingChecklistComponent from './onboarding-checklist.component';

/** Component internals under test (protected in the class). */
interface Editor {
  rows: () => { key: number; title: string; dueDay: number; link: string }[];
  defaultDays: { set: (d: number) => void };
  valid: () => boolean;
  dirty: () => boolean;
  rowErrors: () => { title: boolean; dueDay: boolean }[];
  update: (key: number, patch: object) => void;
  remove: (key: number) => void;
  add: () => void;
  move: (index: number, by: -1 | 1) => void;
  save: () => void;
}

const CHECKLIST: OnboardingChecklist = {
  defaultDays: 30,
  customized: false,
  tasks: [
    { title: 'IT Systems Setup', description: 'Accounts and laptop', dueDay: 2, owner: 'HR', link: 'ACCOUNT' },
    { title: 'Onboarding Review Meeting', description: null, dueDay: 30, owner: 'MANAGER', link: null },
  ],
};

describe('OnboardingChecklistComponent', () => {
  let editor: Editor;
  let saveChecklist: jest.Mock;

  beforeEach(() => {
    saveChecklist = jest.fn(() => of({ ...CHECKLIST, customized: true }));
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: OnboardingService, useValue: { checklist: () => of(CHECKLIST), saveChecklist } }],
    });
    editor = TestBed.createComponent(OnboardingChecklistComponent).componentInstance as unknown as Editor;
  });

  it('starts clean and valid from the loaded checklist', () => {
    expect(editor.rows().map(r => r.title)).toEqual(['IT Systems Setup', 'Onboarding Review Meeting']);
    expect(editor.valid()).toBe(true);
    expect(editor.dirty()).toBe(false);
  });

  it('flags a task due after a shorter default length', () => {
    editor.defaultDays.set(14);

    expect(editor.rowErrors().map(e => e.dueDay)).toEqual([false, true]);
    expect(editor.valid()).toBe(false);
    expect(editor.dirty()).toBe(true);
  });

  it('needs at least one task and a title on each', () => {
    const [first, second] = editor.rows();
    editor.update(first.key, { title: '   ' });
    expect(editor.valid()).toBe(false);

    editor.remove(first.key);
    editor.remove(second.key);
    expect(editor.rows()).toHaveLength(0);
    expect(editor.valid()).toBe(false);
  });

  it('reorders and saves trimmed tasks, unlinked ones as null', () => {
    editor.move(1, -1);
    editor.update(editor.rows()[0].key, { title: '  Review  ' });
    editor.save();

    expect(saveChecklist).toHaveBeenCalledWith({
      defaultDays: 30,
      tasks: [
        { title: 'Review', description: null, dueDay: 30, owner: 'MANAGER', link: null },
        { title: 'IT Systems Setup', description: 'Accounts and laptop', dueDay: 2, owner: 'HR', link: 'ACCOUNT' },
      ],
    });
  });
});
