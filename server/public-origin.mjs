export const DEFAULT_PUBLIC_ORIGIN = 'https://friendship-app-backend-1.onrender.com';

// Render values can be pasted with whitespace, surrounding quotes, or a trailing slash.
export function configurePublicOrigin(env = process.env, warn = console.warn) {
  let value = (env.PUBLIC_ORIGIN || '').trim();
  if ((value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))) value = value.slice(1, -1).trim();
  try {
    const url = new URL(value);
    if (url.protocol !== 'https:' || url.username || url.password ||
        url.pathname !== '/' || url.search || url.hash) throw new Error('Invalid origin');
    env.PUBLIC_ORIGIN = url.origin;
  } catch {
    // Never log the supplied value: an accidentally pasted URL may contain secrets.
    env.PUBLIC_ORIGIN = DEFAULT_PUBLIC_ORIGIN;
    warn('PUBLIC_ORIGIN is missing or invalid; using ' + DEFAULT_PUBLIC_ORIGIN);
  }
  return env.PUBLIC_ORIGIN;
}
