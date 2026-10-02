import { useState, useEffect, useCallback } from 'react';
import { initGoogleAuth, signOut, parseJwt } from './auth';
import { translate as apiTranslate } from './api';
import Header from './components/Header';
import Hero from './components/Hero';
import AuthGate from './components/AuthGate';
import Translator from './components/Translator';
import Footer from './components/Footer';

/**
 * Root application component.
 *
 * State machine:
 *   unauthenticated  → user sees AuthGate with Google Sign-In
 *   authenticated    → user sees the Translator
 *
 * Translation pipeline state:
 *   idle / loading / success / error
 */
export default function App() {
  const [user, setUser]         = useState(null);   // { name, email, picture, sub }
  const [token, setToken]       = useState(null);   // Google ID token string
  const [inputText, setInput]   = useState('');
  const [status, setStatus]     = useState('idle'); // idle | loading | success | error
  const [result, setResult]     = useState(null);   // { meaning, explanation }
  const [error, setError]       = useState(null);   // string
  const [history, setHistory]   = useState([]);     // recent translations

  // ------------------------------------------------------------------ Auth

  const handleCredential = useCallback((response) => {
    const cred = response.credential;
    const payload = parseJwt(cred);
    setToken(cred);
    setUser({
      name:    payload.name    || 'User',
      email:   payload.email   || '',
      picture: payload.picture || '',
      sub:     payload.sub     || '',
    });
    setStatus('idle');
  }, []);

  useEffect(() => {
    // Wait for GIS script to load (it's async in index.html)
    const tryInit = () => {
      if (window.google) {
        initGoogleAuth(handleCredential);
      } else {
        setTimeout(tryInit, 200);
      }
    };
    tryInit();
  }, [handleCredential]);

  const handleSignOut = useCallback(() => {
    signOut(user?.sub, () => {
      setUser(null);
      setToken(null);
      setStatus('idle');
      setResult(null);
      setError(null);
      setHistory([]);
    });
  }, [user?.sub]);

  // ------------------------------------------------------------------ Translation

  const handleTranslate = useCallback(async (text) => {
    if (!text.trim() || !token) return;
    setStatus('loading');
    setResult(null);
    setError(null);

    try {
      const data = await apiTranslate(text, token);
      setResult(data);
      setStatus('success');
      setHistory(prev => [{ ...data, id: Date.now() }, ...prev].slice(0, 8));
    } catch (err) {
      setError(err.message);
      setStatus('error');
    }
  }, [token]);

  const handleChipClick = useCallback((text) => {
    setInput(text);
    handleTranslate(text);
  }, [handleTranslate]);

  const handleHistoryClick = useCallback((item) => {
    setResult(item);
    setStatus('success');
    window.scrollTo({ top: 200, behavior: 'smooth' });
  }, []);

  // ------------------------------------------------------------------ Render

  return (
    <>
      <Header user={user} onSignOut={handleSignOut} />

      <main>
        <div className="container">
          <Hero />

          {!user ? (
            <AuthGate />
          ) : (
            <Translator
              inputText={inputText}
              onInputChange={setInput}
              onTranslate={handleTranslate}
              status={status}
              result={result}
              error={error}
              history={history}
              onChipClick={handleChipClick}
              onHistoryClick={handleHistoryClick}
            />
          )}
        </div>
      </main>

      <Footer />
    </>
  );
}
