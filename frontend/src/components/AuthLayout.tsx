import type { ReactNode } from "react";
import "./AuthLayout.css";

interface AuthLayoutProps {
  children: ReactNode;
}

const ledgerRows = [
  { label: "Alimentação", value: "−45,30" },
  { label: "Transporte", value: "−8,50" },
  { label: "Renda", value: "−500,00" },
  { label: "Transferência", value: "+1200,00" },
];

export default function AuthLayout({ children }: AuthLayoutProps) {
  return (
    <div className="auth-layout">
      <div className="auth-layout__panel">
        <span className="auth-layout__mark">Finance Manager</span>

        <p className="auth-layout__headline">Cada movimento, com o seu lugar.</p>

        <div className="auth-layout__ledger">
          {ledgerRows.map((row) => (
            <div className="auth-layout__ledger-row" key={row.label}>
              <span>{row.label}</span>
              <span>{row.value}</span>
            </div>
          ))}
        </div>
      </div>

      <div className="auth-layout__form-side">{children}</div>
    </div>
  );
}
