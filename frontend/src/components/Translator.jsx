import { useState, useMemo } from 'react';

const EXAMPLE_CHIPS = [
  'no cap fr fr',
  'skibidi rizz',
  'delulu is the solulu',
  'bro is cooked',
  'main character energy',
  'living rent free',
  'let him cook',
  "it's giving aesthetic",
  'ate and left no crumbs',
  'touch grass',
  'npc behavior',
  'pushing 🅿️',
];

export default function Translator({
  inputText,
  onInputChange,
  onTranslate,
  status,
  result,
  error,
  history,
  onChipClick,
  onHistoryClick,
}) {
  const [copied, setCopied] = useState(false);

  const handleKeyDown = (e) => {
    if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') {
      e.preventDefault();
      if (inputText.trim() && status !== 'loading') {
        onTranslate(inputText);
      }
    }
  };

  const handleCopy = () => {
    if (!result) return;
    const textToCopy = `Meaning: ${result.meaning}\n\nExplanation: ${result.explanation}`;
    navigator.clipboard.writeText(textToCopy).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  };

  // Fun heuristic for Brainrot / Cringe meter
  const cringeLevel = useMemo(() => {
    if (!inputText.trim()) return { pct: 15, label: 'MILD', type: 'low' };
    const text = inputText.toLowerCase();
    const extremeTerms = ['skibidi', 'rizz', 'sigma', 'gyatt', 'fanum tax', 'delulu', 'cooked', 'sus', 'bop', 'mewing', 'ratio'];
    const hits = extremeTerms.filter((term) => text.includes(term)).length;

    if (hits >= 2) return { pct: 95, label: 'TERMINAL BRAINROT 💀', type: 'high' };
    if (hits === 1) return { pct: 60, label: 'ELEVATED CRINGE ⚠️', type: 'medium' };
    return { pct: 25, label: 'CHILL SLANG 😎', type: 'low' };
  }, [inputText]);

  return (
    <div className="translator">
      {/* Example Chips */}
      <div className="chips">
        {EXAMPLE_CHIPS.map((chip) => (
          <button
            key={chip}
            type="button"
            className="chip"
            onClick={() => onChipClick(chip)}
          >
            {chip}
          </button>
        ))}
      </div>

      {/* Dual Pane Translator */}
      <div className="dual-pane">
        {/* Left Pane: Input */}
        <div className="pane">
          <div className="pane__header">
            <span className="pane__label">Input — Internet Slang / Meme</span>
          </div>

          <textarea
            className="pane__textarea"
            value={inputText}
            onChange={(e) => onInputChange(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Paste or type any Gen-Z slang, brainrot, meme phrase, or acronym... (Press ⌘+Enter to translate)"
            maxLength={280}
            rows={8}
          />

          <div className="pane__footer">
            <span className={`char-count ${inputText.length > 250 ? 'near-limit' : ''}`}>
              {inputText.length} / 280
            </span>
            <button
              className="btn-primary"
              onClick={() => onTranslate(inputText)}
              disabled={!inputText.trim() || status === 'loading'}
            >
              {status === 'loading' ? (
                <>Decoding...</>
              ) : (
                <>Translate ⚡</>
              )}
            </button>
          </div>
        </div>

        {/* Divider */}
        <div className="pane-divider">
          <span>⚡</span>
        </div>

        {/* Right Pane: Output */}
        <div className="pane pane--output">
          <div className="pane__header">
            <span className="pane__label">Output — Plain English</span>
            {result && (
              <button
                type="button"
                className={`copy-btn ${copied ? 'copied' : ''}`}
                onClick={handleCopy}
              >
                {copied ? '✓ Copied' : '📋 Copy'}
              </button>
            )}
          </div>

          <div className="output-area">
            {status === 'loading' && (
              <div className="output-loading">
                <div className="loading-dots">
                  <span />
                  <span />
                  <span />
                </div>
                <span className="loading-text">Decoding slang with Gemini Flash...</span>
              </div>
            )}

            {status === 'error' && error && (
              <div className="output-error">
                <div className="error-title">Translation Notice</div>
                <div className="error-message">{error}</div>
              </div>
            )}

            {status !== 'loading' && result && (
              <div className="output-result">
                <div className="meaning-block">
                  <span className="block-label">What it means</span>
                  <div className="meaning-text">{result.meaning}</div>
                </div>
                <div className="explanation-block">
                  <span className="block-label">Context & Breakdown</span>
                  <div className="explanation-text">{result.explanation}</div>
                </div>
              </div>
            )}

            {status !== 'loading' && !result && !error && (
              <div className="output-placeholder">
                <span className="output-placeholder__icon">💡</span>
                <p>Select an example chip above or type any slang to see the plain-English translation.</p>
              </div>
            )}
          </div>

          {/* Brainrot / Cringe Gauge */}
          <div className="pane__stats">
            <div className="cringe-meter">
              <span className="cringe-meter__label">Brainrot Level</span>
              <div className="cringe-bar-track">
                <div
                  className={`cringe-bar-fill ${cringeLevel.type}`}
                  style={{ width: `${cringeLevel.pct}%` }}
                />
              </div>
              <span className={`cringe-tag ${cringeLevel.type}`}>
                {cringeLevel.label}
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* History */}
      {history && history.length > 0 && (
        <section className="history-section">
          <h3 className="section-title">Recent Decodes</h3>
          <div className="history-grid">
            {history.map((item) => (
              <div
                key={item.id}
                className="history-card"
                onClick={() => onHistoryClick(item)}
              >
                <div className="history-card__meaning">{item.meaning}</div>
                <div className="history-card__explanation">{item.explanation}</div>
              </div>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
