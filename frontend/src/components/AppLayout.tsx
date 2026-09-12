import type { ReactNode } from "react";
import { NavLink } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import "./AppLayout.css";

export default function AppLayout({ children }: { children: ReactNode }) {
  const { logout } = useAuth();

  return (
    <div className="app-shell">
      <header className="app-shell__header">
        <span className="app-shell__mark">Finance Manager</span>
        <nav className="app-shell__nav">
          <NavLink to="/dashboard" className={({ isActive }) => (isActive ? "active" : undefined)}>
            Dashboard
          </NavLink>
          <NavLink to="/import" className={({ isActive }) => (isActive ? "active" : undefined)}>
            Importar
          </NavLink>
          <NavLink to="/categories" className={({ isActive }) => (isActive ? "active" : undefined)}>
            Categorias
          </NavLink>
        </nav>
        <button className="app-shell__logout" onClick={logout}>
          Sair
        </button>
      </header>
      <main className="app-shell__main">{children}</main>
    </div>
  );
}
