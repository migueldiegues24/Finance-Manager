import { useEffect, useState } from "react";
import { apiFetch } from "../api/client";
import { useAuth } from "../context/AuthContext";
import "./DashboardPage.css";

interface CategoryTotal {
  categoryId: number;
  categoryName: string;
  total: number;
}

interface DashboardSummary {
  month: string;
  totals: CategoryTotal[];
  overallTotal: number;
}

function currentMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

function shiftMonth(month: string, delta: number): string {
  const [year, m] = month.split("-").map(Number);
  const date = new Date(year, m - 1 + delta, 1);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}`;
}

function formatMonthLabel(month: string): string {
  const [year, m] = month.split("-").map(Number);
  const date = new Date(year, m - 1, 1);
  const label = new Intl.DateTimeFormat("pt-PT", { month: "long", year: "numeric" }).format(date);
  return label.charAt(0).toUpperCase() + label.slice(1);
}

function formatCurrency(value: number): string {
  return new Intl.NumberFormat("pt-PT", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value);
}

export default function DashboardPage() {
  const { logout } = useAuth();
  const [month, setMonth] = useState(currentMonth());
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    apiFetch(`/dashboard/summary?month=${month}`)
      .then(async (res) => {
        if (!res.ok) throw new Error("Não foi possível carregar o resumo");
        const data: DashboardSummary = await res.json();
        if (!cancelled) setSummary(data);
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof Error ? err.message : "Algo correu mal");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [month]);

  return (
    <div className="dashboard">
      <header className="dashboard__header">
        <span className="dashboard__mark">Finance Manager</span>
        <button className="dashboard__logout" onClick={logout}>
          Sair
        </button>
      </header>

      <div className="dashboard__month-nav">
        <button onClick={() => setMonth((m) => shiftMonth(m, -1))} aria-label="Mês anterior">
          ‹
        </button>
        <h1>{formatMonthLabel(month)}</h1>
        <button onClick={() => setMonth((m) => shiftMonth(m, 1))} aria-label="Mês seguinte">
          ›
        </button>
      </div>

      {loading && <p className="dashboard__status">A carregar…</p>}
      {error && <p className="dashboard__status dashboard__status--error">{error}</p>}

      {summary && !loading && !error && (
        summary.totals.length === 0 ? (
          <p className="dashboard__status">Sem despesas registadas neste mês.</p>
        ) : (
          <table className="ledger">
            <thead>
              <tr>
                <th>Categoria</th>
                <th>Total</th>
              </tr>
            </thead>
            <tbody>
              {summary.totals.map((row) => (
                <tr key={row.categoryId}>
                  <td>{row.categoryName}</td>
                  <td className="ledger__amount">−{formatCurrency(row.total)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <td>Total do mês</td>
                <td className="ledger__amount">−{formatCurrency(summary.overallTotal)}</td>
              </tr>
            </tfoot>
          </table>
        )
      )}
    </div>
  );
}
