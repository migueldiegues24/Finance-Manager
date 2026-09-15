import { useEffect, useState } from "react";
import { apiFetch } from "../api/client";
import AppLayout from "../components/AppLayout";
import { formatCurrency } from "../utils/format";
import { currentMonth, shiftMonth, formatMonthLabel } from "../utils/date";
import "./DashboardPage.css";
import "./TransactionsPage.css";

interface Transaction {
  id: number;
  date: string;
  description: string;
  amount: number;
  categoryId: number;
  categoryName: string;
}

interface Category {
  id: number;
  name: string;
  defaultCategory: boolean;
}

export default function TransactionsPage() {
  const [month, setMonth] = useState(currentMonth());
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [savingId, setSavingId] = useState<number | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    Promise.all([apiFetch(`/transactions?month=${month}`), apiFetch("/categories")])
      .then(async ([txRes, catRes]) => {
        if (!txRes.ok || !catRes.ok) throw new Error("Não foi possível carregar as transações");
        const [txData, catData] = await Promise.all([txRes.json(), catRes.json()]);
        if (!cancelled) {
          setTransactions(txData);
          setCategories(catData);
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

  async function handleCategoryChange(transactionId: number, categoryId: string) {
    setSavingId(transactionId);
    setError(null);
    try {
      const res = await apiFetch(`/transactions/${transactionId}/category`, {
        method: "PUT",
        body: JSON.stringify({ categoryId: Number(categoryId) }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new Error(body.error ?? "Não foi possível atualizar a categoria");
      }
      const updated: Transaction = await res.json();
      setTransactions((prev) => prev.map((t) => (t.id === updated.id ? updated : t)));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Algo correu mal");
    } finally {
      setSavingId(null);
    }
  }

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

      {!loading && !error && (
        transactions.length === 0 ? (
          <p className="dashboard__status">Sem transações neste mês.</p>
        ) : (
          <table className="ledger transactions-table">
            <thead>
              <tr>
                <th>Data</th>
                <th>Descrição</th>
                <th>Categoria</th>
                <th>Valor</th>
              </tr>
            </thead>
            <tbody>
              {transactions.map((t) => (
                <tr key={t.id}>
                  <td>{t.date}</td>
                  <td>{t.description}</td>
                  <td>
                    <select
                      value={t.categoryId}
                      disabled={savingId === t.id}
                      onChange={(e) => handleCategoryChange(t.id, e.target.value)}
                    >
                      {categories.map((c) => (
                        <option key={c.id} value={c.id}>
                          {c.name}
                        </option>
                      ))}
                    </select>
                  </td>
                  <td className={t.amount < 0 ? "ledger__amount" : "ledger__amount ledger__amount--income"}>
                    {t.amount < 0 ? "−" : "+"}
                    {formatCurrency(Math.abs(t.amount))}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )
      )}
    </AppLayout>
  );
}
