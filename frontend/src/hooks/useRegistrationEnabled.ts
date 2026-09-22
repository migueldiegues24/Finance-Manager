import { useEffect, useState } from "react";
import { API_BASE } from "../api/client";

// Se o registo público está ligado (GET /api/auth/registration, público).
// null enquanto não há resposta ou se o pedido falhar: nesse caso o formulário
// continua visível e, se o registo estiver mesmo desligado, o submit recebe um
// 403 com a mensagem do backend. Um só pedido, sem novas tentativas.
export function useRegistrationEnabled(): boolean | null {
  const [enabled, setEnabled] = useState<boolean | null>(null);

  useEffect(() => {
    let cancelled = false;

    fetch(`${API_BASE}/auth/registration`)
      .then((res) => (res.ok ? res.json() : Promise.reject(new Error("registration"))))
      .then((body: { enabled?: unknown }) => {
        if (!cancelled && typeof body.enabled === "boolean") setEnabled(body.enabled);
      })
      .catch(() => {});

    return () => {
      cancelled = true;
    };
  }, []);

  return enabled;
}
