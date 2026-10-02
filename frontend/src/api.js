/**
 * MemeSpeak API client
 *
 * All calls go through Vite's dev proxy (/api → localhost:8080)
 * so there are zero CORS issues during development.
 *
 * The Google ID token is passed as a Bearer token on every request.
 */

const API_BASE = '/api/v1';

/** POST /api/v1/translate */
export async function translate(text, token) {
  const res = await fetch(`${API_BASE}/translate`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({ text }),
  });

  const data = await res.json();

  if (!res.ok) {
    // Map known error codes to user-friendly messages
    const messages = {
      UNSAFE_INPUT:       'Please keep your request respectful 🙏',
      RATE_LIMIT_EXCEEDED:'Slow down! You\'ve hit the limit (30/min). Try again shortly.',
      AI_UNAVAILABLE:     'The AI translator is taking a break. Try again in a moment.',
      VALIDATION_ERROR:   data.message || 'Invalid request.',
      UNAUTHORIZED:       'Your session expired. Please sign in again.',
    };
    throw new Error(messages[data.error] || data.message || 'Something went wrong.');
  }

  return data; // { meaning, explanation }
}

/** GET /api/v1/me — verify auth and get user profile */
export async function getMe(token) {
  const res = await fetch(`${API_BASE}/me`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error('Auth failed');
  return res.json(); // { googleId, email, name }
}
