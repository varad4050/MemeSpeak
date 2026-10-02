import { useEffect } from 'react';

/**
 * Auth gate shown to unauthenticated users.
 *
 * The Google Sign-In button is rendered INTO #g_id_signin
 * by the GIS library (called in App.jsx via initGoogleAuth).
 * We mount a placeholder div with that ID and GIS fills it.
 */
export default function AuthGate() {
  useEffect(() => {
    // Re-trigger GIS button render if the element mounts after initialization
    if (window.google) {
      const el = document.getElementById('g_id_signin');
      if (el && el.children.length === 0) {
        window.google.accounts.id.renderButton(el, {
          theme: 'outline',
          size: 'large',
          text: 'signin_with',
          shape: 'rectangular',
        });
      }
    }
  }, []);

  return (
    <div className="auth-gate">
      <div className="auth-gate__icon">🔐</div>
      <h2 className="auth-gate__title">Sign in to start translating</h2>
      <p className="auth-gate__sub">
        Use your Google account to decode the internet.
        Free, fast, and your data stays private.
      </p>

      {/* GIS renders the real button here */}
      <div className="auth-gate__google">
        <div id="g_id_signin"></div>
      </div>

      <p className="auth-gate__note">
        We only store your Google ID — never your password or raw input text.
      </p>
    </div>
  );
}
