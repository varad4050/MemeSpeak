export default function Header({ user, onSignOut }) {
  return (
    <header className="header">
      <div className="container">
        <div className="header__inner">
          {/* Logo */}
          <a href="/" className="header__logo">
            <div className="header__logo-icon">🗣️</div>
            <span className="header__logo-text">MemeSpeak</span>
            <span className="header__logo-badge">Beta</span>
          </a>

          {/* Right side: user or nothing */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            {user ? (
              <>
                <div className="user-chip">
                  {user.picture ? (
                    <img src={user.picture} alt={user.name} referrerPolicy="no-referrer" />
                  ) : (
                    <div style={{
                      width: 28, height: 28, borderRadius: '50%',
                      background: '#FFD43B', border: '1.5px solid #111',
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      fontFamily: 'Space Grotesk, sans-serif', fontWeight: 700, fontSize: '0.8rem'
                    }}>
                      {user.name?.[0]?.toUpperCase() || '?'}
                    </div>
                  )}
                  <span className="user-chip__name">{user.name}</span>
                </div>
                <button className="sign-out-btn" onClick={onSignOut}>
                  Sign out
                </button>
              </>
            ) : null}
          </div>
        </div>
      </div>
    </header>
  );
}
