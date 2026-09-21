import { useCallback, useId, useRef, useState } from "react";
import { useActiveMonths } from "../hooks/useActiveMonths";
import { currentMonth, formatMonthLabel, shiftMonth } from "../utils/date";
import MonthPicker from "./MonthPicker";

interface MonthNavProps {
  month: string;
  onChange: (month: string) => void;
}

export default function MonthNav({ month, onChange }: MonthNavProps) {
  const today = currentMonth();
  const isCurrent = month === today;
  const [open, setOpen] = useState(false);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const pickerId = useId();
  const activeMonths = useActiveMonths(open);

  const close = useCallback((restoreFocus: boolean) => {
    setOpen(false);
    if (restoreFocus) triggerRef.current?.focus();
  }, []);

  return (
    <div className="month-nav">
      <div className="month-nav__anchor">
        <h1 className="page-header__title month-nav__title">
          <button
            ref={triggerRef}
            type="button"
            className="month-nav__trigger"
            aria-haspopup="dialog"
            aria-expanded={open}
            aria-controls={pickerId}
            onClick={() => setOpen((o) => !o)}
          >
            {formatMonthLabel(month)}
            <span className="month-nav__caret" aria-hidden="true">
              ▾
            </span>
          </button>
        </h1>
        {open && (
          <MonthPicker
            id={pickerId}
            selected={month}
            activeMonths={activeMonths}
            onSelect={onChange}
            onClose={close}
          />
        )}
      </div>
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
