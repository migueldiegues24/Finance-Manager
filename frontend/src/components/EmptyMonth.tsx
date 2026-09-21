import { Link } from "react-router-dom";
import { useActiveMonths } from "../hooks/useActiveMonths";
import { formatMonthLabel } from "../utils/date";

interface EmptyMonthProps {
  month: string;
  onChange: (month: string) => void;
}

// Estado vazio de um mês sem movimentos, com as ações que fazem sentido:
// importar um extrato ou saltar para o último mês que tem dados.
export default function EmptyMonth({ month, onChange }: EmptyMonthProps) {
  const activeMonths = useActiveMonths(true);
  const latest = activeMonths ? [...activeMonths].sort().at(-1) : undefined;
  const jumpTo = latest && latest !== month ? latest : undefined;

  return (
    <div className="empty-state">
      <h2 className="empty-state__title">Sem movimentos em {formatMonthLabel(month).toLowerCase()}.</h2>
      <p className="empty-state__text">Importa um extrato para ver este mês.</p>
      <div className="empty-state__actions">
        <Link className="btn btn--primary" to="/import">
          Importar extrato
        </Link>
        {jumpTo && (
          <button type="button" className="btn btn--ghost" onClick={() => onChange(jumpTo)}>
            Ir para {formatMonthLabel(jumpTo).toLowerCase()}
          </button>
        )}
      </div>
    </div>
  );
}
