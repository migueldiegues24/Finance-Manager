import { useState, type ChangeEvent, type FormEvent } from "react";
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
          transactions: chosen.map(({ date, movementDate, description, amount, balanceAfter, occurrence }) => ({
            date,
            movementDate,
            description,
            amount,
            balanceAfter,
            occurrence,
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

  return (
    <AppLayout>
      <h1 className="import-page__title">Importar extrato</h1>
      <p className="import-page__subtitle">{BANK_HINTS[bank]}</p>

      <form className="import-page__upload" onSubmit={handleAnalyze}>
        <select
          className="import-page__bank"
          value={bank}
          onChange={(event) => {
            setBank(event.target.value as Bank);
            setTransactions(null);
            setResult(null);
          }}
          aria-label="Banco"
        >
          <option value="CGD">CGD</option>
          <option value="GENERIC">CSV genérico</option>
        </select>
        <label className="import-page__file-label">
          {file ? file.name : "Escolher ficheiro"}
          <input type="file" accept=".csv,.xls,text/csv" onChange={handleFileChange} hidden />
        </label>
        <button type="submit" disabled={!file || analyzing}>
          {analyzing ? "A analisar…" : "Analisar"}
        </button>
      </form>

      {error && <p className="dashboard__status dashboard__status--error">{error}</p>}

      {result && (
        <p className="import-page__result">
          Importação concluída: {result.transactionsSaved} transações guardadas de "{result.filename}"
          {result.duplicatesSkipped > 0 && ` (${result.duplicatesSkipped} já existiam e foram ignoradas)`}.
        </p>
      )}

      {transactions && (
        <>
          <table className="ledger">
            <thead>
              <tr>
                <th></th>
                <th>Data</th>
                <th>Descrição</th>
                <th>Valor</th>
                {hasBalance && <th>Saldo</th>}
              </tr>
            </thead>
            <tbody>
              {transactions.map((t) => (
                <tr key={t.hash}>
                  <td>
                    <input
                      type="checkbox"
                      checked={selected.has(t.hash)}
                      disabled={t.duplicate}
                      onChange={() => toggle(t.hash)}
                    />
                  </td>
                  <td title={t.movementDate && t.movementDate !== t.date ? `Data do movimento: ${t.movementDate}` : undefined}>
                    {t.date}
                  </td>
                  <td>
                    {t.description}
                    {t.duplicate && <span className="import-page__duplicate-tag"> (já importado)</span>}
                  </td>
                  <td className={t.amount < 0 ? "ledger__amount" : "ledger__amount ledger__amount--income"}>
                    {t.amount < 0 ? "−" : "+"}
                    {formatCurrency(Math.abs(t.amount))}
                  </td>
                  {hasBalance && <td className="ledger__amount">{t.balanceAfter !== null ? formatCurrency(t.balanceAfter) : ""}</td>}
                </tr>
              ))}
            </tbody>
          </table>

          <button
            className="import-page__confirm"
            onClick={handleConfirm}
            disabled={confirming || selected.size === 0}
          >
            {confirming ? "A confirmar…" : `Confirmar importação (${selected.size})`}
          </button>
        </>
      )}
    </AppLayout>
  );
}
