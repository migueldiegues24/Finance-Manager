import { useState, type ChangeEvent, type FormEvent } from "react";
import { apiFetch } from "../api/client";
import AppLayout from "../components/AppLayout";
import { formatCurrency } from "../utils/format";
import "./ImportPage.css";

interface ParsedTransaction {
  date: string;
  description: string;
  amount: number;
  hash: string;
  duplicate: boolean;
}

interface ImportSummary {
  importId: number;
  filename: string;
  transactionsSaved: number;
}

export default function ImportPage() {
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

      const res = await apiFetch("/imports/parse", { method: "POST", body: formData });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new Error(body.error ?? "Não foi possível ler o ficheiro");
      }

      const data: { filename: string; transactions: ParsedTransaction[] } = await res.json();
      setFilename(data.filename);
      setTransactions(data.transactions);
      // Duplicados ficam desmarcados por defeito, o utilizador pode voltar
      // a marcá-los se for mesmo intencional (ex.: dois cafés no mesmo dia).
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
          transactions: chosen.map(({ date, description, amount }) => ({ date, description, amount })),
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

  return (
    <AppLayout>
      <h1 className="import-page__title">Importar extrato</h1>
      <p className="import-page__subtitle">
        Carrega um CSV com as colunas date, description, amount.
      </p>

      <form className="import-page__upload" onSubmit={handleAnalyze}>
        <label className="import-page__file-label">
          {file ? file.name : "Escolher ficheiro CSV"}
          <input type="file" accept=".csv" onChange={handleFileChange} hidden />
        </label>
        <button type="submit" disabled={!file || analyzing}>
          {analyzing ? "A analisar…" : "Analisar"}
        </button>
      </form>

      {error && <p className="dashboard__status dashboard__status--error">{error}</p>}

      {result && (
        <p className="import-page__result">
          Importação concluída: {result.transactionsSaved} transações guardadas de "{result.filename}".
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
              </tr>
            </thead>
            <tbody>
              {transactions.map((t) => (
                <tr key={t.hash}>
                  <td>
                    <input
                      type="checkbox"
                      checked={selected.has(t.hash)}
                      onChange={() => toggle(t.hash)}
                    />
                  </td>
                  <td>{t.date}</td>
                  <td>
                    {t.description}
                    {t.duplicate && <span className="import-page__duplicate-tag"> (já importado)</span>}
                  </td>
                  <td className={t.amount < 0 ? "ledger__amount" : "ledger__amount ledger__amount--income"}>
                    {t.amount < 0 ? "−" : "+"}
                    {formatCurrency(Math.abs(t.amount))}
                  </td>
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
