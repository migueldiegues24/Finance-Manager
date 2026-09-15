import { useEffect, useState } from "react";
import { apiFetch } from "../api/client";
import AppLayout from "../components/AppLayout";
import { formatCurrency } from "../utils/format";
import { currentMonth, shiftMonth, formatMonthLabel } from "../utils/date";
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

export default function DashboardPage() {
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
    <AppLayout>
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
    </AppLayout>
  );
}
