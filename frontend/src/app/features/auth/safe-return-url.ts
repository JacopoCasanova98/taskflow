/** Query parameters are untrusted; accept only application-local absolute paths. */
export function safeReturnUrl(value: string | null): string {
  if (!value?.startsWith('/') || value.startsWith('//')) return '/';
  try {
    const decoded = decodeURIComponent(value);
    // Reject browser-normalized separators/control characters, including encoded variants.
    // eslint-disable-next-line no-control-regex -- Intentionally reject control characters in navigation input.
    if (decoded.startsWith('//') || /[\\\u0000-\u0020\u007f]/.test(decoded)) return '/';
  } catch {
    return '/';
  }
  return value;
}
