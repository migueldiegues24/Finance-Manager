import type { ReactNode } from "react";
import "./AuthLayout.css";

// Página de livro-razão estilizada: margem dupla, linhas pautadas, descrições
// e valores como traços, e o total com a linha dupla. Decorativa (aria-hidden);
// usa currentColor para seguir a cor do painel nos dois temas.
function LedgerIllustration() {
  const rows = [
    { text: 118, amount: 46 },
    { text: 86, amount: 38 },
    { text: 134, amount: 58 },
    { text: 72, amount: 30 },
    { text: 104, amount: 52 },
    { text: 92, amount: 42 },
  ];
  const top = 44;
  const step = 28;

  return (
    <svg
      className="auth-layout__illustration"
      viewBox="0 0 320 260"
      aria-hidden="true"
      focusable="false"
    >
      <rect x="0.5" y="0.5" width="319" height="259" fill="none" stroke="currentColor" strokeOpacity="0.35" />
      {/* Cabeçalho e margem dupla */}
      <line x1="16" y1="30" x2="304" y2="30" stroke="currentColor" strokeOpacity="0.55" />
      <line x1="44" y1="12" x2="44" y2="248" stroke="currentColor" strokeOpacity="0.35" />
      <line x1="48" y1="12" x2="48" y2="248" stroke="currentColor" strokeOpacity="0.35" />
      <line x1="232" y1="12" x2="232" y2="248" stroke="currentColor" strokeOpacity="0.2" />
      <rect x="60" y="16" width="54" height="5" rx="1" fill="currentColor" fillOpacity="0.45" />
      <rect x="264" y="16" width="30" height="5" rx="1" fill="currentColor" fillOpacity="0.45" />
      {rows.map((row, i) => {
        const y = top + i * step;
        return (
          <g key={i}>
            <line x1="16" y1={y + 12} x2="304" y2={y + 12} stroke="currentColor" strokeOpacity="0.14" />
            <rect x="20" y={y} width="16" height="4" rx="1" fill="currentColor" fillOpacity="0.3" />
            <rect x="60" y={y} width={row.text} height="4" rx="1" fill="currentColor" fillOpacity="0.55" />
            <rect x={294 - row.amount} y={y} width={row.amount} height="4" rx="1" fill="currentColor" fillOpacity="0.8" />
          </g>
        );
      })}
      {/* Total: linha dupla e valor */}
      <line x1="232" y1="214" x2="304" y2="214" stroke="currentColor" strokeOpacity="0.8" />
      <line x1="232" y1="218" x2="304" y2="218" stroke="currentColor" strokeOpacity="0.8" />
      <rect x="60" y="228" width="42" height="5" rx="1" fill="currentColor" fillOpacity="0.6" />
      <rect x="240" y="228" width="54" height="5" rx="1" fill="currentColor" />
    </svg>
  );
}

export default function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="auth-layout">
      <aside className="auth-layout__panel" aria-label="Finance Manager">
        <p className="auth-layout__mark">Finance Manager</p>
        <div className="auth-layout__panel-body">
          <p className="auth-layout__headline">
            Importa extratos, organiza os movimentos por categoria e acompanha cada mês.
          </p>
          <LedgerIllustration />
        </div>
      </aside>

      <main className="auth-layout__form-side">
        {/* Em ecrãs estreitos o painel desaparece e a marca fica por cima do formulário. */}
        <p className="auth-layout__mobile-mark" aria-hidden="true">
          Finance Manager
        </p>
        {children}
      </main>
    </div>
  );
}
