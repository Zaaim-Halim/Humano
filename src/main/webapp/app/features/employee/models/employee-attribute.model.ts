/** A custom key/value attribute on an employee — `…/employees/{id}/attributes`. */
export interface EmployeeAttribute {
  key: string;
  value: string;
}

/** Replace-all payload — the supplied list becomes the employee's complete set. */
export interface UpdateEmployeeAttributesRequest {
  attributes: EmployeeAttribute[];
}
