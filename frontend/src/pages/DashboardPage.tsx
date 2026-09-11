import { useAuth } from "../context/AuthContext";

export default function DashboardPage() {
  const { logout } = useAuth();

  return (
    <div style={{ padding: "3rem", maxWidth: "640px" }}>
      <h1>Dashboard</h1>
      <p style={{ color: "var(--color-ink-soft)", marginTop: "0.75rem" }}>
        Os totais por categoria e mês entram aqui no próximo passo.
      </p>
      <button
        onClick={logout}
        style={{
          marginTop: "2rem",
          background: "transparent",
          border: "1px solid var(--color-line)",
          padding: "0.6rem 1.1rem",
          cursor: "pointer",
        }}
      >
        Sair
      </button>
    </div>
  );
}
