export default function Footer() {
  return (
    <footer className="footer">
      <div className="container">
        <div className="footer__inner">
          <div className="footer__copy">
            © {new Date().getFullYear()} MemeSpeak. Powered by Gemini Flash. No slang left untranslated.
          </div>
          <div className="footer__links">
            <a
              href="/swagger-ui.html"
              target="_blank"
              rel="noopener noreferrer"
              className="footer__link"
            >
              OpenAPI Docs
            </a>
            <a
              href="https://aistudio.google.com/"
              target="_blank"
              rel="noopener noreferrer"
              className="footer__link"
            >
              Gemini Flash AI
            </a>
          </div>
        </div>
      </div>
    </footer>
  );
}
