/** A training programme — `GET /api/hr/trainings` (the fields the onboarding picker needs). */
export interface Training {
  id: string;
  name: string;
  provider: string | null;
  startDate: string | null;
  endDate: string | null;
}
