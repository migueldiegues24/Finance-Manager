import { currentMonth, formatMonthLabel, shiftMonth } from "../utils/date";

interface MonthNavProps {
  month: string;
  onChange: (month: string) => void;
}

export default function MonthNav({ month, onChange }: MonthNavProps) {
  const today = currentMonth();
  const isCurrent = month === today;

  return (
    <div className="month-nav">
      <h1 className="page-header__title month-nav__title">{formatMonthLabel(month)}</h1>
      <div className="month-nav__controls">
        <button
          className="btn btn--ghost btn--sm month-nav__today"
          onClick={() => onChange(today)}
          disabled={isCurrent}
        >
          Mês atual
        </button>
        <button
          className="btn btn--ghost month-nav__btn"
          onClick={() => onChange(shiftMonth(month, -1))}
          aria-label="Mês anterior"
        >
          ‹
        </button>
        <button
          className="btn btn--ghost month-nav__btn"
          onClick={() => onChange(shiftMonth(month, 1))}
          disabled={isCurrent}
          aria-label="Mês seguinte"
        >
          ›
        </button>
      </div>
    </div>
  );
}
