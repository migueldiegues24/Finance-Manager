import { useEffect, useState } from "react";
import { apiFetch } from "../api/client";

// Meses ("YYYY-MM") com transações do utilizador. Só pede ao backend quando
// enabled passa a true (ex.: ao abrir o seletor), e volta a pedir de cada vez,
// para refletir importações recentes. null enquanto não há resposta.
export function useActiveMonths(enabled: boolean): Set<string> | null {
  const [months, setMonths] = useState<Set<string> | null>(null);

  useEffect(() => {
    if (!enabled) return;
    let cancelled = false;

    apiFetch("/dashboard/months")
      .then((res) => (res.ok ? res.json() : Promise.reject(new Error("months"))))
      .then((list: string[]) => {
        if (!cancelled) setMonths(new Set(list));
      })
      // Sem esta informação o seletor continua a funcionar, só não marca meses.
      .catch(() => {});

    return () => {
      cancelled = true;
    };
  }, [enabled]);

  return months;
}
