import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { apiFetch } from "../api/client";
import AppLayout from "../components/AppLayout";
import EmptyMonth from "../components/EmptyMonth";
import MonthNav from "../components/MonthNav";
import { useMonthParam } from "../hooks/useMonthParam";
import { useTheme } from "../theme/themeContext";
import { categoryStyle } from "../utils/categoryColor";
import { formatCurrency } from "../utils/format";
import "./TransactionsPage.css";
import Notice from "../components/Notice";
import { AppError, apiFailure, toNotice, type NoticeContent } from "../api/errors";

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
  color?: string | null;
}

export default function TransactionsPage() {
  const [month, setMonth] = useMonthParam();
  const [searchParams, setSearchParams] = useSearchParams();
  const { theme } = useTheme();
  const onlyUncategorized = searchParams.get("filter") === "uncategorized";
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<NoticeContent | null>(null);
  const [savingId, setSavingId] = useState<number | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    Promise.all([apiFetch(`/transactions?month=${month}`), apiFetch("/categories")])
      .then(async ([txRes, catRes]) => {
        if (!txRes.ok || !catRes.ok) throw new AppError("Não foi possível carregar as transações.");
        const [txData, catData] = await Promise.all([txRes.json(), catRes.json()]);
        if (!cancelled) {
          setTransactions(txData);
          setCategories(catData);
        }
      })
      .catch((err) => {
        if (!cancelled) setError(toNotice(err));
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
        throw await apiFailure(res, "Não foi possível mudar a categoria.");
      }
      const updated: Transaction = await res.json();
      setTransactions((prev) => prev.map((t) => (t.id === updated.id ? updated : t)));
    } catch (err) {
      setError(toNotice(err));
    } finally {
      setSavingId(null);
    }
  }

  // "Sem Categoria" é a categoria protegida (defaultCategory). O aviso conta
  // receitas e despesas.
  const uncategorizedId = categories.find((c) => c.defaultCategory)?.id ?? null;
  const uncategorizedCount = transactions.filter((t) => t.categoryId === uncategorizedId).length;
  const visible = onlyUncategorized ? transactions.filter((t) => t.categoryId === uncategorizedId) : transactions;
  const assignable = categories.filter((c) => c.id !== uncategorizedId);
  const colorOf = new Map(categories.map((c) => [c.id, c.color]));

  function setOnlyUncategorized(value: boolean) {
    setSearchParams((prev) => {
      const params = new URLSearchParams(prev);
      if (value) params.set("filter", "uncategorized");
      else params.delete("filter");
      return params;
    });
  }

  return (
    <AppLayout>
      <header className="page-header">
        <p className="page-header__eyebrow">Movimentos do mês</p>
        <MonthNav month={month} onChange={setMonth} />
      </header>

      {loading && <Notice title="A carregar…" />}
      {error && <Notice tone="error" title={error.title} detail={error.detail} />}

      {!loading && !error && transactions.length === 0 && <EmptyMonth month={month} onChange={setMonth} />}

      {!loading && !error && transactions.length > 0 && (
        <>
          {uncategorizedCount > 0 && (
            <Notice
              tone="attention"
              title={
                onlyUncategorized
                  ? `⚠ A mostrar ${uncategorizedCount === 1 ? "o único" : `os ${uncategorizedCount}`} sem categoria.`
                  : `⚠ ${uncategorizedCount} sem categoria.`
              }
              action={
                <button
                  type="button"
                  className="btn btn--ghost btn--sm"
                  onClick={() => setOnlyUncategorized(!onlyUncategorized)}
                  aria-pressed={onlyUncategorized}
                >
                  {onlyUncategorized ? "Mostrar todos" : uncategorizedCount === 1 ? "Mostrar só este" : "Mostrar só estes"}
                </button>
              }
            />
          )}

          {onlyUncategorized && uncategorizedCount === 0 && (
            <Notice
              tone="success"
              title="Todos os movimentos têm categoria."
              action={
                <button type="button" className="btn btn--ghost btn--sm" onClick={() => setOnlyUncategorized(false)}>
                  Mostrar todos
                </button>
              }
            />
          )}

          {visible.length > 0 && (
            <table className="ledger ledger--stack transactions-table" role="table">
              <thead role="rowgroup">
                <tr role="row">
                  <th role="columnheader" scope="col">Data</th>
                  <th role="columnheader" scope="col">Descrição</th>
                  <th role="columnheader" scope="col">Categoria</th>
                  <th role="columnheader" scope="col" className="ledger__num">Valor</th>
                </tr>
              </thead>
              <tbody role="rowgroup">
                {visible.map((t) => {
                  const uncategorized = t.categoryId === uncategorizedId;
                  return (
                    <tr role="row" key={t.id} className={uncategorized ? "is-uncategorized" : undefined}>
                      <td role="cell" className="ledger__cell--date">{t.date}</td>
                      <td role="cell" className="ledger__cell--desc">{t.description}</td>
                      <td role="cell" className="ledger__cell--category">
                        <span className="transactions-table__category" style={categoryStyle(t.categoryId, colorOf.get(t.categoryId), theme)}>
                          {uncategorized ? (
                            <span className="tag tag--attention">⚠ Sem categoria</span>
                          ) : (
                            <span className="dot" aria-hidden="true" />
                          )}
                          <select
                            className="field transactions-table__select"
                            value={uncategorized ? "" : t.categoryId}
                            disabled={savingId === t.id}
                            onChange={(e) => handleCategoryChange(t.id, e.target.value)}
                            aria-label={
                              uncategorized ? `Escolher categoria para ${t.description}` : `Categoria de ${t.description}`
                            }
                          >
                            {uncategorized && (
                              <option value="" disabled>
                                Escolher categoria…
                              </option>
                            )}
                            {(uncategorized ? assignable : categories).map((c) => (
                              <option key={c.id} value={c.id}>
                                {c.name}
                              </option>
                            ))}
                          </select>
                        </span>
                      </td>
                      <td
                        role="cell"
                        className={
                          t.amount < 0
                            ? "ledger__num ledger__cell--amount ledger__amount"
                            : "ledger__num ledger__cell--amount ledger__amount ledger__amount--income"
                        }
                      >
                        {t.amount < 0 ? "−" : "+"}
                        {formatCurrency(Math.abs(t.amount))}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </>
      )}
    </AppLayout>
  );
}
