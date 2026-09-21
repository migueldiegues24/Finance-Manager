import { useState, type ChangeEvent, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { apiFetch } from "../api/client";
import AppLayout from "../components/AppLayout";
import { formatCurrency } from "../utils/format";
import "./ImportPage.css";

type Bank = "CGD" | "GENERIC";

interface ParsedTransaction {
  date: string;
  movementDate: string | null;
  description: string;
  amount: number;
  balanceAfter: number | null;
  occurrence: number;
  hash: string;
  duplicate: boolean;
}

interface ImportSummary {
  importId: number;
  filename: string;
  transactionsSaved: number;
  duplicatesSkipped: number;
  importedAsDuplicate: number;
}

const BANK_HINTS: Record<Bank, string> = {
  CGD: "Exporta a \"Consulta de movimentos\" na app da CGD (o ficheiro \"XLS\" serve, é um CSV), sem filtros.",
  GENERIC: "Carrega um CSV com as colunas date, description, amount.",
};

export default function ImportPage() {
  const [bank, setBank] = useState<Bank>("CGD");
  const [file, setFile] = useState<File | null>(null);
  const [filename, setFilename] = useState("");
  const [transactions, setTransactions] = useState<ParsedTransaction[] | null>(null);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [analyzing, setAnalyzing] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<ImportSummary | null>(null);

  function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    setFile(event.target.files?.[0] ?? null);
    setResult(null);
    setTransactions(null);
  }

  async function handleAnalyze(event: FormEvent) {
    event.preventDefault();
    if (!file) return;

    setError(null);
    setAnalyzing(true);

    try {
      const formData = new FormData();
      formData.append("file", file);
      formData.append("bank", bank);

      const res = await apiFetch("/imports/parse", { method: "POST", body: formData });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new Error(body.error ?? "Não foi possível ler o ficheiro");
      }

      const data: { filename: string; transactions: ParsedTransaction[] } = await res.json();
      setFilename(data.filename);
      setTransactions(data.transactions);
      // Movimentos já importados ficam desmarcados (e o servidor ignora-os
      // de qualquer forma). Movimentos iguais no mesmo ficheiro, como dois
      // cafés no mesmo dia, têm hashes diferentes e não são duplicados.
      setSelected(new Set(data.transactions.filter((t) => !t.duplicate).map((t) => t.hash)));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Algo correu mal");
    } finally {
      setAnalyzing(false);
    }
  }

  function toggle(hash: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(hash)) {
        next.delete(hash);
      } else {
        next.add(hash);
      }
      return next;
    });
  }

  // Marca todos os movimentos novos. Os já importados ficam como estão:
  // marcá-los é sempre uma escolha explícita, linha a linha.
  function selectAll() {
    if (!transactions) return;
    setSelected((prev) => {
      const next = new Set(prev);
      transactions.filter((t) => !t.duplicate).forEach((t) => next.add(t.hash));
      return next;
    });
  }

  function selectNone() {
    setSelected(new Set());
  }

  async function handleConfirm() {
    if (!transactions) return;

    setError(null);
    setConfirming(true);

    try {
      const chosen = transactions.filter((t) => selected.has(t.hash));

      const res = await apiFetch("/imports/confirm", {
        method: "POST",
        body: JSON.stringify({
          filename,
          bank,
          // allowDuplicate: o utilizador marcou de propósito um movimento que já existe.
          transactions: chosen.map(({ date, movementDate, description, amount, balanceAfter, occurrence, duplicate }) => ({
            date,
            movementDate,
            description,
            amount,
            balanceAfter,
            occurrence,
            allowDuplicate: duplicate,
          })),
        }),
      });

      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new Error(body.error ?? "Não foi possível confirmar a importação");
      }

      const data: ImportSummary = await res.json();
      setResult(data);
      setTransactions(null);
      setFile(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Algo correu mal");
    } finally {
      setConfirming(false);
    }
  }

  const hasBalance = transactions?.some((t) => t.balanceAfter !== null) ?? false;
  const newCount = transactions?.filter((t) => !t.duplicate).length ?? 0;
  const duplicateCount = (transactions?.length ?? 0) - newCount;
  const chosen = transactions?.filter((t) => selected.has(t.hash)) ?? [];
  const chosenDuplicates = chosen.filter((t) => t.duplicate).length;
  const allNewSelected = chosen.length - chosenDuplicates === newCount;
  const selectedIncome = chosen.filter((t) => t.amount > 0).reduce((sum, t) => sum + t.amount, 0);
  const selectedExpenses = chosen.filter((t) => t.amount < 0).reduce((sum, t) => sum - t.amount, 0);

  return (
    <AppLayout>
      <header className="page-header">
        <p className="page-header__eyebrow">Importação</p>
        <h1 className="page-header__title">Importar extrato</h1>
        <p className="page-header__subtitle">{BANK_HINTS[bank]}</p>
      </header>

      <form className="import-page__upload" onSubmit={handleAnalyze}>
        <label className="visually-hidden" htmlFor="import-bank">
          Banco
        </label>
        <select
          id="import-bank"
          className="field import-page__bank"
          value={bank}
          onChange={(event) => {
            setBank(event.target.value as Bank);
            setTransactions(null);
            setResult(null);
          }}
        >
          <option value="CGD">CGD</option>
          <option value="GENERIC">CSV genérico</option>
        </select>
        <label className="btn btn--ghost import-page__file-label">
          <span className="import-page__file-name">{file ? file.name : "Escolher ficheiro"}</span>
          <input
            className="visually-hidden"
            type="file"
            accept=".csv,.xls,text/csv"
            onChange={handleFileChange}
          />
        </label>
        <button className="btn btn--primary" type="submit" disabled={!file || analyzing}>
          {analyzing ? "A analisar…" : "Analisar"}
        </button>
      </form>

      {error && <p className="status status--error" role="alert">{error}</p>}

      {result && (
        <div className="status status--success import-page__result" role="status">
          <span>
            Importação concluída: {result.transactionsSaved} transações guardadas de "{result.filename}"
            {result.duplicatesSkipped > 0 && ` (${result.duplicatesSkipped} já existiam e foram ignoradas)`}
            {result.importedAsDuplicate > 0 &&
              `; ${result.importedAsDuplicate} ${result.importedAsDuplicate === 1 ? "foi importada" : "foram importadas"} de novo como duplicado`}
            .
          </span>
          <Link className="btn btn--ghost btn--sm" to="/transactions">
            Ver transações
          </Link>
        </div>
      )}

      {transactions && (
        <div className="import-page__review">
          {duplicateCount > 0 && (
            <p className="status status--attention" role="note">
              <strong>
                ⚠ {duplicateCount === 1 ? "1 movimento já existe" : `${duplicateCount} movimentos já existem`}.
              </strong>{" "}
              Se {duplicateCount === 1 ? "o marcares, fica duplicado" : "os marcares, ficam duplicados"} e os totais
              contam duas vezes.
            </p>
          )}
          <div className="import-page__toolbar">
            <p className="import-page__count">
              {transactions.length} movimentos
              {duplicateCount > 0 && ` · ${duplicateCount} já importados`}
            </p>
            <div className="import-page__select">
              <button
                type="button"
                className="btn btn--link"
                onClick={selectAll}
                disabled={newCount === 0 || allNewSelected}
              >
                {duplicateCount > 0 ? "Selecionar todos os novos" : "Selecionar tudo"}
              </button>
              <button type="button" className="btn btn--link" onClick={selectNone} disabled={selected.size === 0}>
                Nenhum
              </button>
            </div>
          </div>
          <table className="ledger ledger--stack ledger--interactive import-table" role="table">
            <thead role="rowgroup">
              <tr role="row">
                <th role="columnheader" scope="col" className="ledger__cell--check">
                  <span className="visually-hidden">Importar</span>
                </th>
                <th role="columnheader" scope="col">Data</th>
                <th role="columnheader" scope="col">Descrição</th>
                <th role="columnheader" scope="col" className="ledger__num">Valor</th>
                {hasBalance && <th role="columnheader" scope="col" className="ledger__num">Saldo</th>}
              </tr>
            </thead>
            <tbody role="rowgroup">
              {transactions.map((t) => (
                <tr
                  role="row"
                  key={t.hash}
                  className={t.duplicate ? (selected.has(t.hash) ? "is-duplicate is-forced" : "is-duplicate") : undefined}
                >
                  <td role="cell" className="ledger__cell--check">
                    <input
                      type="checkbox"
                      checked={selected.has(t.hash)}
                      onChange={() => toggle(t.hash)}
                      aria-label={
                        t.duplicate
                          ? `Importar de novo ${t.description} de ${t.date} (já importado, ficará duplicado)`
                          : `Importar ${t.description} de ${t.date}`
                      }
                    />
                  </td>
                  <td
                    role="cell"
                    className="ledger__cell--date"
                    title={t.movementDate && t.movementDate !== t.date ? `Data do movimento: ${t.movementDate}` : undefined}
                  >
                    {t.date}
                  </td>
                  <td role="cell" className="ledger__cell--desc">
                    {t.description}
                    {t.duplicate && (
                      <span
                        className={
                          selected.has(t.hash)
                            ? "tag tag--attention import-page__duplicate-tag"
                            : "tag import-page__duplicate-tag"
                        }
                      >
                        {selected.has(t.hash) ? "⚠ Já importado · vai duplicar" : "Já importado"}
                      </span>
                    )}
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
                  {hasBalance && (
                    <td role="cell" className="ledger__num ledger__cell--balance">
                      {t.balanceAfter !== null && (
                        <>
                          <span className="ledger__cell-label">Saldo </span>
                          {formatCurrency(t.balanceAfter)}
                        </>
                      )}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>

          {/* Barra fixa: com extratos longos a confirmação fica sempre visível.
              .import-page__review reserva espaço em baixo para ela. */}
          <div className="import-bar" role="region" aria-label="Confirmar importação">
            <div className="app-shell__inner import-bar__inner">
              <p className="import-bar__summary" aria-live="polite">
                <strong>{selected.size} selecionados</strong>
                {chosenDuplicates > 0 && (
                  <span className="import-bar__duplicates">
                    ⚠ incl. {chosenDuplicates} já {chosenDuplicates === 1 ? "importado" : "importados"}
                  </span>
                )}
                <span className="import-bar__totals">
                  <span className="ledger__amount ledger__amount--income">+{formatCurrency(selectedIncome)}</span>
                  <span className="ledger__amount">−{formatCurrency(selectedExpenses)}</span>
                </span>
              </p>
              <button
                className="btn btn--primary import-bar__confirm"
                onClick={handleConfirm}
                disabled={confirming || selected.size === 0}
              >
                {confirming ? "A confirmar…" : `Confirmar importação (${selected.size})`}
              </button>
            </div>
          </div>
        </div>
      )}
    </AppLayout>
  );
}
