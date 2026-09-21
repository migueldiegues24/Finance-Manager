import { useEffect, useRef, useState, type FocusEvent, type KeyboardEvent } from "react";
import { currentMonth } from "../utils/date";
import { isFutureMonth, monthIndex, monthOf, monthYear } from "../utils/period";
import "./MonthPicker.css";

const COLUMNS = 4;

const shortMonth = new Intl.DateTimeFormat("pt-PT", { month: "short" });
const longMonth = new Intl.DateTimeFormat("pt-PT", { month: "long", year: "numeric" });

function shortLabel(index: number): string {
  return shortMonth.format(new Date(2000, index, 1)).replace(".", "");
}

function longLabel(month: string): string {
  return longMonth.format(new Date(monthYear(month), monthIndex(month), 1));
}

interface MonthPickerProps {
  id: string;
  selected: string;
  activeMonths: Set<string> | null;
  onSelect: (month: string) => void;
  // restoreFocus: devolver o foco ao botão que abriu o seletor.
  onClose: (restoreFocus: boolean) => void;
}

// Popover com a grelha dos 12 meses de um ano. Não é modal: Tab para fora
// ou um clique fora fecham-no. Teclado na grelha: setas, Home/End,
// PageUp/PageDown (ano), Enter/Espaço (escolher), Esc (fechar).
// Preparado para a vista anual: o cabeçalho do ano é o sítio de um futuro
// "Ver ano inteiro".
export default function MonthPicker({ id, selected, activeMonths, onSelect, onClose }: MonthPickerProps) {
  const today = currentMonth();
  const maxYear = monthYear(today);

  const [viewYear, setViewYear] = useState(monthYear(selected));
  const [focusIndex, setFocusIndex] = useState(monthIndex(selected));
  const dialogRef = useRef<HTMLDivElement>(null);
  const cellRefs = useRef<(HTMLButtonElement | null)[]>([]);
  // Só move o foco para a grelha quando a mudança veio do teclado (ou da abertura).
  const focusGrid = useRef(true);

  useEffect(() => {
    if (focusGrid.current) {
      cellRefs.current[focusIndex]?.focus();
      focusGrid.current = false;
    }
  }, [viewYear, focusIndex]);

  // Clique fora fecha, sem roubar o foco ao elemento clicado.
  useEffect(() => {
    function handlePointerDown(event: PointerEvent) {
      const target = event.target as Node;
      if (dialogRef.current?.contains(target)) return;
      // O botão que abre o seletor trata do seu próprio clique (alternar).
      if ((target as Element).closest?.(`[aria-controls="${id}"]`)) return;
      onClose(false);
    }
    document.addEventListener("pointerdown", handlePointerDown);
    return () => document.removeEventListener("pointerdown", handlePointerDown);
  }, [id, onClose]);

  function moveTo(year: number, index: number) {
    if (year > maxYear) return;
    focusGrid.current = true;
    setViewYear(year);
    setFocusIndex(index);
  }

  function handleGridKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    let year = viewYear;
    let index = focusIndex;

    switch (event.key) {
      case "ArrowLeft":
        index -= 1;
        break;
      case "ArrowRight":
        index += 1;
        break;
      case "ArrowUp":
        index -= COLUMNS;
        break;
      case "ArrowDown":
        index += COLUMNS;
        break;
      case "Home":
        index = 0;
        break;
      case "End":
        index = 11;
        break;
      case "PageUp":
        year -= 1;
        break;
      case "PageDown":
        year += 1;
        break;
      default:
        return;
    }
    event.preventDefault();

    // Passar do fim/início da grelha continua no ano seguinte/anterior.
    if (index < 0) {
      year -= 1;
      index += 12;
    } else if (index > 11) {
      year += 1;
      index -= 12;
    }
    moveTo(year, index);
  }

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === "Escape") {
      event.preventDefault();
      onClose(true);
    }
  }

  // Tab para fora do seletor fecha-o. O botão que o abre fica de fora,
  // porque o seu próprio clique já alterna o estado.
  function handleBlur(event: FocusEvent<HTMLDivElement>) {
    const next = event.relatedTarget as Element | null;
    if (!next || dialogRef.current?.contains(next)) return;
    if (next.closest(`[aria-controls="${id}"]`)) return;
    onClose(false);
  }

  // Se o botão "ano seguinte" ficar desativado, o foco passa para a grelha
  // em vez de se perder (e com ele o Esc).
  function showNextYear() {
    if (viewYear + 1 >= maxYear) focusGrid.current = true;
    setViewYear(viewYear + 1);
  }

  function choose(month: string) {
    if (isFutureMonth(month, today)) return;
    onSelect(month);
    onClose(true);
  }

  const rows = [0, 1, 2].map((row) => Array.from({ length: COLUMNS }, (_, col) => row * COLUMNS + col));

  return (
    <div
      ref={dialogRef}
      id={id}
      className="month-picker"
      role="dialog"
      aria-modal="false"
      aria-label="Escolher mês"
      onKeyDown={handleKeyDown}
      onBlur={handleBlur}
    >
      <div className="month-picker__header">
        <button
          type="button"
          className="btn btn--ghost month-picker__year-btn"
          onClick={() => setViewYear((y) => y - 1)}
          aria-label="Ano anterior"
        >
          ‹
        </button>
        <p className="month-picker__year" id={`${id}-year`} aria-live="polite">
          {viewYear}
        </p>
        <button
          type="button"
          className="btn btn--ghost month-picker__year-btn"
          onClick={showNextYear}
          disabled={viewYear >= maxYear}
          aria-label="Ano seguinte"
        >
          ›
        </button>
      </div>

      <div role="grid" aria-labelledby={`${id}-year`} className="month-picker__grid" onKeyDown={handleGridKeyDown}>
        {rows.map((cells, rowIndex) => (
          <div role="row" className="month-picker__row" key={rowIndex}>
            {cells.map((index) => {
              const month = monthOf(viewYear, index);
              const isSelected = month === selected;
              const isToday = month === today;
              const isFuture = isFutureMonth(month, today);
              const hasData = activeMonths?.has(month) ?? false;
              const details = [hasData && "com movimentos", isToday && "mês atual", isFuture && "indisponível"]
                .filter(Boolean)
                .join(", ");

              const classes = ["month-picker__cell"];
              if (isSelected) classes.push("is-selected");
              if (isToday) classes.push("is-today");
              if (hasData) classes.push("has-data");

              return (
                <div role="gridcell" aria-selected={isSelected} key={month}>
                  <button
                    type="button"
                    ref={(el) => {
                      cellRefs.current[index] = el;
                    }}
                    className={classes.join(" ")}
                    tabIndex={index === focusIndex ? 0 : -1}
                    aria-disabled={isFuture || undefined}
                    aria-label={details ? `${longLabel(month)}, ${details}` : longLabel(month)}
                    onClick={() => choose(month)}
                    onFocus={() => setFocusIndex(index)}
                  >
                    {shortLabel(index)}
                  </button>
                </div>
              );
            })}
          </div>
        ))}
      </div>

      <div className="month-picker__footer">
        <span className="month-picker__legend">
          <span className="month-picker__legend-dot" aria-hidden="true" /> com movimentos
        </span>
        <button type="button" className="btn btn--link" onClick={() => choose(today)}>
          Mês atual
        </button>
      </div>
    </div>
  );
}
