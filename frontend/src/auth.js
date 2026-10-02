/**
 * Google Identity Services (GIS) helpers.
 *
 * Uses the GIS library loaded via <script> in index.html.
 * The client ID is read from VITE_GOOGLE_CLIENT_ID env variable
 * (set in .env.local).
 *
 * Flow:
 *   1. initGoogleAuth(callback) — initialize GIS, render sign-in button,
 *      show One Tap if returning user.
 *   2. callback({ credential }) — the credential IS the Google ID Token (JWT).
 *      We pass it directly to the backend as Authorization: Bearer <token>.
 *   3. signOut() — revoke and clear local state.
 */

const CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID || '';

export function initGoogleAuth(onCredential) {
  if (!window.google) {
    console.error('[MemeSpeak] Google Identity Services script not loaded.');
    return;
  }

  if (!CLIENT_ID) {
    console.warn(
      '[MemeSpeak] VITE_GOOGLE_CLIENT_ID is not set.\n' +
      'Create frontend/.env.local with:\n' +
      '  VITE_GOOGLE_CLIENT_ID=your-google-client-id.apps.googleusercontent.com'
    );
  }

  window.google.accounts.id.initialize({
    client_id: CLIENT_ID,
    callback: onCredential,
    auto_select: true,        // auto sign in returning users
    cancel_on_tap_outside: false,
  });

  // Render the standard Google button into #g_id_signin
  const btnContainer = document.getElementById('g_id_signin');
  if (btnContainer) {
    window.google.accounts.id.renderButton(btnContainer, {
      theme: 'outline',
      size: 'large',
      text: 'signin_with',
      shape: 'rectangular',
      logo_alignment: 'left',
    });
  }

  // Show One Tap prompt for returning users
  window.google.accounts.id.prompt();
}

export function signOut(googleId, callback) {
  if (window.google && googleId) {
    window.google.accounts.id.revoke(googleId, () => {
      callback?.();
    });
  } else {
    callback?.();
  }
}

/** Decode JWT payload (no verification — backend does this) */
export function parseJwt(token) {
  try {
    const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(atob(base64));
  } catch {
    return {};
  }
}
