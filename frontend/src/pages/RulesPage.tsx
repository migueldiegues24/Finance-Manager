import { useCallback, useEffect, useRef, useState } from "react";
import { apiFetch } from "../api/client";
import AppLayout from "../components/AppLayout";
import RuleDialog from "../components/RuleDialog";
import { categoryStyle } from "../utils/categoryColor";
import "./CategoriesPage.css";
import Notice from "../components/Notice";
import { AppError, apiFailure, toNotice, type NoticeContent } from "../api/errors";

interface Category {
  id: number;
  name: string;
  defaultCategory: boolean;
}

interface Rule {
  id: number;
  keyword: string;
  categoryId: number;
  categoryName: string;
  priority: number;
}

// Sem regra correspondente, a importação usa "Outros" se existir; se tiver
// sido apagada, "Sem Categoria" (ImportWriter.resolveFallbackCategory).
function fallbackName(categories: Category[]): string {
  return categories.some((c) => c.name === "Outros")
    ? "Outros"
    : (categories.find((c) => c.defaultCategory)?.name ?? "Sem Categoria");
}

async function fetchRulesAndCategories(): Promise<[Rule[], Category[]]> {
  const [ruleRes, catRes] = await Promise.all([apiFetch("/rules"), apiFetch("/categories")]);
  if (!ruleRes.ok || !catRes.ok) throw new AppError("Não foi possível carregar as regras.");
  return Promise.all([ruleRes.json(), catRes.json()]);
}

export default function RulesPage() {
  const [rules, setRules] = useState<Rule[] | null>(null);
  const [categories, setCategories] = useState<Category[]>([]);
  const [error, setError] = useState<NoticeContent | null>(null);
  const [dialogKey, setDialogKey] = useState<number | null>(null);
  const newButtonRef = useRef<HTMLButtonElement>(null);

  const reload = useCallback(async () => {
    const [ruleData, catData] = await fetchRulesAndCategories();
    setRules(ruleData);
    setCategories(catData);
  }, []);

  useEffect(() => {
    let cancelled = false;
    fetchRulesAndCategories()
      .then(([ruleData, catData]) => {
        if (!cancelled) {
          setRules(ruleData);
          setCategories(catData);
        }
      })
      .catch((err) => {
        if (!cancelled) setError(toNotice(err));
      });
    return () => {
      cancelled = true;
    };
  }, []);

  async function deleteRule(id: number) {
    setError(null);
    try {
      const res = await apiFetch(`/rules/${id}`, { method: "DELETE" });
      if (!res.ok) throw await apiFailure(res, "Não foi possível apagar a regra.");
      await reload();
      newButtonRef.current?.focus();
    } catch (err) {
      setError(toNotice(err));
    }
  }

  return (
    <AppLayout>
      <header className="page-header page-header--with-action">
        <div>
          <p className="page-header__eyebrow">Configuração</p>
          <h1 className="page-header__title">Regras</h1>
          <p className="page-header__subtitle">Movimentos com a palavra-chave vão para a categoria.</p>
          <p className="status__detail page-header__detail">Maiúsculas e acentos são ignorados.</p>
        </div>
        <button ref={newButtonRef} type="button" className="btn btn--primary" onClick={() => setDialogKey(Date.now())}>
          Nova regra
        </button>
      </header>

      {error && <Notice tone="error" title={error.title} detail={error.detail} />}
      {rules === null && !error && <Notice title="A carregar…" />}

      {rules && rules.length === 0 && (
        <div className="empty-state">
          <h2 className="empty-state__title">Ainda não há regras.</h2>
          <p className="empty-state__text">Sem regras, tudo vai para {fallbackName(categories)}.</p>
          <div className="empty-state__actions">
            <button type="button" className="btn btn--primary" onClick={() => setDialogKey(Date.now())}>
              Criar a primeira regra
            </button>
          </div>
        </div>
      )}

      {rules && rules.length > 0 && (
        <table className="ledger">
          <thead>
            <tr>
              <th>Palavra-chave</th>
              <th>Categoria</th>
              <th>
                <span className="visually-hidden">Ações</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {rules.map((rule) => (
              <tr key={rule.id}>
                <td>{rule.keyword}</td>
                <td>
                  <span className="tag" style={categoryStyle(rule.categoryId)}>
                    {rule.categoryName}
                  </span>
                </td>
                <td className="categories-page__actions">
                  <button
                    type="button"
                    className="btn btn--link btn--danger"
                    onClick={() => deleteRule(rule.id)}
                    aria-label={`Apagar a regra ${rule.keyword}`}
                  >
                    Apagar
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {dialogKey !== null && (
        <RuleDialog
          key={dialogKey}
          open
          categories={categories}
          onClose={() => setDialogKey(null)}
          onSaved={reload}
          returnFocusRef={newButtonRef}
        />
      )}
    </AppLayout>
  );
}
