import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { apiFetch } from "../api/client";
import AppLayout from "../components/AppLayout";
import EmptyMonth from "../components/EmptyMonth";
import MonthNav from "../components/MonthNav";
import { useMonthParam } from "../hooks/useMonthParam";
import { categoryStyle } from "../utils/categoryColor";
import { formatCurrency } from "../utils/format";
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
  totalIncome: number;
  totalExpenses: number;
  net: number;
}

interface Category {
  id: number;
  defaultCategory: boolean;
}

const percentFormat = new Intl.NumberFormat("pt-PT", { style: "percent", maximumFractionDigits: 0 });

function netCaption(net: number): string {
  if (net > 0) return "Entrou mais do que saiu";
  if (net < 0) return "Saiu mais do que entrou";
  return "Entradas e saídas equilibradas";
}

export default function DashboardPage() {
  const [month, setMonth] = useMonthParam();
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [uncategorizedId, setUncategorizedId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    Promise.all([apiFetch(`/dashboard/summary?month=${month}`), apiFetch("/categories")])
      .then(async ([summaryRes, categoriesRes]) => {
        if (!summaryRes.ok || !categoriesRes.ok) throw new Error("Não foi possível carregar o resumo");
        const [data, categories]: [DashboardSummary, Category[]] = await Promise.all([
          summaryRes.json(),
          categoriesRes.json(),
        ]);
        if (!cancelled) {
          setSummary(data);
          setUncategorizedId(categories.find((c) => c.defaultCategory)?.id ?? null);
        }
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

  const isEmpty = summary !== null && summary.totalIncome === 0 && summary.totalExpenses === 0;

  return (
    <AppLayout>
      <header className="page-header">
        <p className="page-header__eyebrow">Resumo mensal</p>
        <MonthNav month={month} onChange={setMonth} />
      </header>

      {loading && <p className="status" role="status">A carregar…</p>}
      {error && <p className="status status--error" role="alert">{error}</p>}

      {summary && !loading && !error && isEmpty && <EmptyMonth month={month} onChange={setMonth} />}

      {summary && !loading && !error && !isEmpty && (
        <>
          <section className="summary-cards" aria-label="Resumo do mês">
            <div className="summary-card summary-card--income">
              <p className="summary-card__label">Receitas</p>
              <p className="summary-card__value ledger__amount ledger__amount--income">
                +{formatCurrency(summary.totalIncome)}
              </p>
            </div>
            <div className="summary-card summary-card--expense">
              <p className="summary-card__label">Despesas</p>
              <p className="summary-card__value ledger__amount">−{formatCurrency(summary.totalExpenses)}</p>
            </div>
            <div className="summary-card summary-card--net">
              <p className="summary-card__label">Balanço do mês</p>
              <p
                className={
                  summary.net < 0
                    ? "summary-card__value ledger__amount"
                    : "summary-card__value ledger__amount ledger__amount--income"
                }
              >
                {summary.net < 0 ? "−" : "+"}
                {formatCurrency(Math.abs(summary.net))}
              </p>
              <p className="summary-card__caption">{netCaption(summary.net)}</p>
            </div>
          </section>

          <section className="section dashboard__categories" aria-labelledby="dashboard-categories-title">
            <h2 className="section__title" id="dashboard-categories-title">
              Despesas por categoria
            </h2>

            {summary.totals.length === 0 ? (
              <p className="status">Sem despesas registadas neste mês.</p>
            ) : (
              <table className="ledger dashboard__table">
                <thead>
                  <tr>
                    <th>Categoria</th>
                    <th className="ledger__num">Peso</th>
                    <th className="ledger__num">Total</th>
                  </tr>
                </thead>
                <tbody>
                  {summary.totals.map((row) => {
                    const share = summary.overallTotal > 0 ? row.total / summary.overallTotal : 0;
                    const uncategorized = row.categoryId === uncategorizedId;
                    return (
                      <tr key={row.categoryId} style={categoryStyle(row.categoryId)}>
                        <td>
                          <div className="dashboard__category">
                            {uncategorized ? (
                              <span className="dashboard__category-name">
                                <span className="tag tag--attention">⚠ {row.categoryName}</span>
                                <Link
                                  className="btn btn--link dashboard__categorize"
                                  to={`/transactions?month=${month}&filter=uncategorized`}
                                >
                                  Categorizar →
                                </Link>
                              </span>
                            ) : (
                              <span className="label-with-dot dashboard__category-name">
                                <span className="dot" aria-hidden="true" />
                                {row.categoryName}
                              </span>
                            )}
                            <span className={uncategorized ? "share-bar share-bar--attention" : "share-bar"} aria-hidden="true">
                              <span className="share-bar__fill" style={{ width: `${share * 100}%` }} />
                            </span>
                          </div>
                        </td>
                        <td className="ledger__num dashboard__share">{percentFormat.format(share)}</td>
                        <td className="ledger__num ledger__amount">−{formatCurrency(row.total)}</td>
                      </tr>
                    );
                  })}
                </tbody>
                <tfoot>
                  <tr>
                    <td>Total das despesas</td>
                    <td />
                    <td className="ledger__num ledger__amount">−{formatCurrency(summary.overallTotal)}</td>
                  </tr>
                </tfoot>
              </table>
            )}
          </section>
        </>
      )}
    </AppLayout>
  );
}
