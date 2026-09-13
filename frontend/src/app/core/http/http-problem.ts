import { HttpErrorResponse } from '@angular/common/http';

interface HttpProblem {
  readonly code?: string;
}

/** Match known codes only; never render arbitrary server details. */
export function isHttpProblem(error: unknown, status: number, code: string): boolean {
  return (
    error instanceof HttpErrorResponse &&
    error.status === status &&
    typeof error.error === 'object' &&
    error.error !== null &&
    (error.error as HttpProblem).code === code
  );
}

/** Extract field names only. Feature code supplies safe copy and an allowlist. */
export function httpValidationFields(error: unknown): readonly string[] | null {
  if (!isHttpProblem(error, 400, 'VALIDATION_FAILED')) return null;
  const violations: unknown = (error as HttpErrorResponse).error.violations;
  if (!Array.isArray(violations)) return [];
  return violations.map((violation: unknown) =>
    typeof violation === 'object' &&
    violation !== null &&
    'field' in violation &&
    typeof violation.field === 'string'
      ? violation.field
      : '',
  );
}
