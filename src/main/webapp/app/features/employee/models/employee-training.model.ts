/** `com.humano.domain.enumeration.hr.TrainingStatus`. */
export type TrainingStatus = 'PLANNED' | 'COMPLETED' | 'CANCELLED';

/** `EmployeeTrainingResponse` — one employee's enrolment in a training. */
export interface EmployeeTraining {
  id: string;
  employeeId: string;
  employeeName: string | null;
  trainingId: string;
  trainingName: string | null;
  status: TrainingStatus;
  description: string | null;
  completionDate: string | null;
  feedback: string | null;
}
