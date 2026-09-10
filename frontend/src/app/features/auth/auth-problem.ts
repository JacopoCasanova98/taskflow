import { HttpErrorResponse } from '@angular/common/http';

interface AuthProblem {
  readonly code?: string;
}

/** Match known codes only; never render arbitrary server details. */
export function isAuthProblem(error: unknown, status: number, code: string): boolean {
  return (
    error instanceof HttpErrorResponse &&
    error.status === status &&
    typeof error.error === 'object' &&
    error.error !== null &&
    (error.error as AuthProblem).code === code
  );
}
