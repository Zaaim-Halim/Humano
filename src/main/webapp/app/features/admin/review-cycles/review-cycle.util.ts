import { ReviewCycle, ReviewCyclePhase } from 'app/features/employee';

/** The cycle's deadline for its current phase; `null` in a draft or once feedback is over. */
export function phaseDeadline(cycle: ReviewCycle): string | null {
  const d = cycle.deadlines;
  switch (cycle.phase) {
    case 'SELF_ASSESSMENT':
      return d.selfAssessmentDeadline;
    case 'MANAGER_REVIEW':
      return d.managerReviewDeadline;
    case 'CALIBRATION':
      return d.calibrationDeadline;
    case 'FEEDBACK_DELIVERY':
      return d.feedbackDeadline;
    case 'GOAL_SETTING':
      return cycle.endDate;
    default:
      return null;
  }
}

/**
 * The phases this cycle walks through: calibration only when it has a calibration deadline, goal
 * setting only once the cycle is in it (HR's choice after feedback meetings).
 */
export function cyclePhases(cycle: ReviewCycle): ReviewCyclePhase[] {
  return [
    'DRAFT',
    'SELF_ASSESSMENT',
    'MANAGER_REVIEW',
    ...(cycle.deadlines.calibrationDeadline ? (['CALIBRATION'] as const) : []),
    'FEEDBACK_DELIVERY',
    ...(cycle.phase === 'GOAL_SETTING' ? (['GOAL_SETTING'] as const) : []),
    'COMPLETED',
  ];
}
