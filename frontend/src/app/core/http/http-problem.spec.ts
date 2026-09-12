import { HttpErrorResponse } from '@angular/common/http';
import { httpValidationFields } from './http-problem';

describe('HTTP validation field extraction', () => {
  it('extracts only field names without details or messages', () => {
    expect(
      httpValidationFields(
        new HttpErrorResponse({
          status: 400,
          error: {
            code: 'VALIDATION_FAILED',
            detail: 'Secret',
            violations: [{ field: 'title', message: 'Secret' }, { field: 'unknown' }],
          },
        }),
      ),
    ).toEqual(['title', 'unknown']);
  });
  it.each([null, {}, 'invalid', [{ field: 42 }, null, 'invalid']])(
    'safely handles malformed violations %j',
    (violations) => {
      const result = httpValidationFields(
        new HttpErrorResponse({ status: 400, error: { code: 'VALIDATION_FAILED', violations } }),
      );
      expect(result?.every((field) => field === '')).toBe(true);
    },
  );
  it('ignores unrelated failures', () => {
    expect(httpValidationFields(new Error('Secret'))).toBeNull();
    expect(
      httpValidationFields(
        new HttpErrorResponse({ status: 400, error: { code: 'MALFORMED_REQUEST' } }),
      ),
    ).toBeNull();
  });
});
