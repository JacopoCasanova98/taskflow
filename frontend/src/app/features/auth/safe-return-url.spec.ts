import { safeReturnUrl } from './safe-return-url';

describe('safeReturnUrl', () => {
  it.each(['/', '/boards/123?filter=open', '/boards/123?filter=open#task'])('accepts %s', (url) => {
    expect(safeReturnUrl(url)).toBe(url);
  });
  it.each([
    null,
    '',
    'https://evil.example',
    'http://evil.example',
    '//evil.example',
    'javascript:alert(1)',
    'evil.example/path',
    '/%2fevil.example',
    '/%5cevil.example',
    '/bad%0aurl',
    '/bad%zz',
    '/\\evil.example',
  ])('falls back for %s', (url) => {
    expect(safeReturnUrl(url)).toBe('/');
  });
});
