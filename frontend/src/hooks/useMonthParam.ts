import { useCallback, useEffect } from "react";
import { useSearchParams } from "react-router-dom";
import { currentMonth } from "../utils/date";
import { parseMonthParam } from "../utils/period";

// Mês selecionado, guardado em ?month=YYYY-MM para ser partilhado entre
// páginas e funcionar com o "voltar" do browser. Sem parâmetro, é o mês atual.
export function useMonthParam(): [string, (month: string) => void] {
  const [searchParams, setSearchParams] = useSearchParams();
  const raw = searchParams.get("month");
  const month = parseMonthParam(raw) ?? currentMonth();

  // Valor inválido ou futuro: remove-o sem criar uma entrada no histórico.
  useEffect(() => {
    if (raw !== null && parseMonthParam(raw) === null) {
      setSearchParams(
        (prev) => {
          const next = new URLSearchParams(prev);
          next.delete("month");
          return next;
        },
        { replace: true },
      );
    }
  }, [raw, setSearchParams]);

  const setMonth = useCallback(
    (next: string) => {
      setSearchParams((prev) => {
        const params = new URLSearchParams(prev);
        params.set("month", next);
        return params;
      });
    },
    [setSearchParams],
  );

  return [month, setMonth];
}
