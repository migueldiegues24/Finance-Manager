import type { ReactNode } from "react";
import { Link, NavLink } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import "./AppLayout.css";

const NAV_ITEMS = [
  { to: "/dashboard", label: "Dashboard" },
  { to: "/transactions", label: "Transações" },
  { to: "/import", label: "Importar" },
  { to: "/categories", label: "Categorias" },
];

export default function AppLayout({ children }: { children: ReactNode }) {
  const { logout } = useAuth();

  return (
    <div className="app-shell">
      <header className="app-shell__header">
        <div className="app-shell__inner app-shell__bar">
          <div className="app-shell__primary">
            <Link to="/dashboard" className="app-shell__mark">
              Finance Manager
            </Link>
            <nav className="app-shell__nav" aria-label="Principal">
              {NAV_ITEMS.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
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
