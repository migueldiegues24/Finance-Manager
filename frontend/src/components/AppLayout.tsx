import type { ReactNode } from "react";
import { Link, NavLink, useSearchParams } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import "./AppLayout.css";

// keepMonth: páginas que partilham o mês selecionado (?month=YYYY-MM).
const NAV_ITEMS = [
  { to: "/dashboard", label: "Dashboard", keepMonth: true },
  { to: "/transactions", label: "Transações", keepMonth: true },
  { to: "/import", label: "Importar", keepMonth: false },
  { to: "/categories", label: "Categorias", keepMonth: false },
];

export default function AppLayout({ children }: { children: ReactNode }) {
  const { logout } = useAuth();
  const [searchParams] = useSearchParams();
  const month = searchParams.get("month");

  return (
    <div className="app-shell">
      <header className="app-shell__header">
        <div className="app-shell__bar">
          <div className="app-shell__primary">
            <Link to="/dashboard" className="app-shell__mark">
              Finance Manager
            </Link>
            <nav className="app-shell__nav" aria-label="Principal">
              {NAV_ITEMS.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.keepMonth && month ? { pathname: item.to, search: `?month=${month}` } : item.to}
                  className={({ isActive }) => (isActive ? "app-shell__link active" : "app-shell__link")}
                >
                  {item.label}
                </NavLink>
              ))}
            </nav>
          </div>
          {/* Zona do utilizador: por agora só "Sair"; mais tarde conta, definições e tema. */}
          <div className="app-shell__user">
            <button className="btn btn--sm app-shell__logout" onClick={logout}>
              Sair
            </button>
          </div>
        </div>
      </header>
      <main className="app-shell__inner app-shell__main">{children}</main>
    </div>
  );
}
