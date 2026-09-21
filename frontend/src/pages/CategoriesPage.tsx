import { useEffect, useRef, useState, type FormEvent } from "react";
import { apiFetch } from "../api/client";
import AppLayout from "../components/AppLayout";
import { categoryStyle } from "../utils/categoryColor";
import "./CategoriesPage.css";

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

export default function CategoriesPage() {
  const [categories, setCategories] = useState<Category[]>([]);
  const [rules, setRules] = useState<Rule[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [newCategoryName, setNewCategoryName] = useState("");
  const [creatingCategory, setCreatingCategory] = useState(false);

  const [editingId, setEditingId] = useState<number | null>(null);
  const [editingName, setEditingName] = useState("");

  const [newRuleKeyword, setNewRuleKeyword] = useState("");
  const keywordInputRef = useRef<HTMLInputElement>(null);
  const [newRuleCategoryId, setNewRuleCategoryId] = useState("");
  const [creatingRule, setCreatingRule] = useState(false);

  async function loadAll() {
    setError(null);
    try {
      const [catRes, ruleRes] = await Promise.all([apiFetch("/categories"), apiFetch("/rules")]);
      if (!catRes.ok || !ruleRes.ok) throw new Error("Não foi possível carregar categorias e regras");
      setCategories(await catRes.json());
      setRules(await ruleRes.json());
    } catch (err) {
      setError(err instanceof Error ? err.message : "Algo correu mal");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadAll();
  }, []);

  async function handleCreateCategory(event: FormEvent) {
    event.preventDefault();
    if (!newCategoryName.trim()) return;

    setCreatingCategory(true);
    setError(null);
    try {
      const res = await apiFetch("/categories", {
        method: "POST",
        body: JSON.stringify({ name: newCategoryName.trim() }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new Error(body.error ?? "Não foi possível criar a categoria");
      }
      setNewCategoryName("");
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Algo correu mal");
    } finally {
      setCreatingCategory(false);
    }
  }

  function startEditing(category: Category) {
    setEditingId(category.id);
    setEditingName(category.name);
  }

  async function saveEditing(id: number) {
    if (!editingName.trim()) return;

    setError(null);
    try {
      const res = await apiFetch(`/categories/${id}`, {
        method: "PUT",
        body: JSON.stringify({ name: editingName.trim() }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new Error(body.error ?? "Não foi possível editar a categoria");
      }
      setEditingId(null);
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Algo correu mal");
    }
  }

  async function deleteCategory(id: number) {
    const confirmed = window.confirm(
      'Apagar esta categoria? As transações associadas passam para "Sem Categoria".',
    );
    if (!confirmed) return;

    setError(null);
    try {
      const res = await apiFetch(`/categories/${id}`, { method: "DELETE" });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new Error(body.error ?? "Não foi possível apagar a categoria");
      }
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Algo correu mal");
    }
  }

  async function handleCreateRule(event: FormEvent) {
    event.preventDefault();
    if (!newRuleKeyword.trim() || !newRuleCategoryId) return;

    setCreatingRule(true);
    setError(null);
    try {
      const res = await apiFetch("/rules", {
        method: "POST",
        body: JSON.stringify({
          keyword: newRuleKeyword.trim(),
          categoryId: Number(newRuleCategoryId),
        }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new Error(body.error ?? "Não foi possível criar a regra");
      }
      setNewRuleKeyword("");
      setNewRuleCategoryId("");
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Algo correu mal");
    } finally {
      setCreatingRule(false);
    }
  }

  async function deleteRule(id: number) {
    setError(null);
    try {
      const res = await apiFetch(`/rules/${id}`, { method: "DELETE" });
      if (!res.ok) throw new Error("Não foi possível apagar a regra");
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Algo correu mal");
    }
  }

  if (loading) {
    return (
      <AppLayout>
        <p className="status" role="status">A carregar…</p>
      </AppLayout>
    );
  }

  return (
    <AppLayout>
      <header className="page-header">
        <p className="page-header__eyebrow">Configuração</p>
        <h1 className="page-header__title">Categorias e regras</h1>
      </header>

      {error && <p className="status status--error" role="alert">{error}</p>}

      <section aria-labelledby="categories-title">
        <h2 className="section__title" id="categories-title">Categorias</h2>
        <p className="section__subtitle">As categorias protegidas não podem ser renomeadas nem apagadas.</p>

        <table className="ledger">
          <thead>
            <tr>
              <th>Nome</th>
              <th>
                <span className="visually-hidden">Ações</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {categories.map((category) => (
              <tr key={category.id}>
                <td>
                  {editingId === category.id ? (
                    <input
                      className="field categories-page__inline-input"
                      aria-label="Nome da categoria"
                      value={editingName}
                      onChange={(e) => setEditingName(e.target.value)}
                      autoFocus
                    />
                  ) : category.defaultCategory ? (
                    <span className="tag tag--attention">⚠ {category.name}</span>
                  ) : (
                    <span className="label-with-dot" style={categoryStyle(category.id)}>
                      <span className="dot" aria-hidden="true" />
                      {category.name}
                    </span>
                  )}
                </td>
                <td className="categories-page__actions">
                  {category.defaultCategory ? (
                    <span className="categories-page__protected">protegida</span>
                  ) : editingId === category.id ? (
                    <>
                      <button className="btn btn--link" onClick={() => saveEditing(category.id)}>
                        Guardar
                      </button>
                      <button className="btn btn--link" onClick={() => setEditingId(null)}>
                        Cancelar
                      </button>
                    </>
                  ) : (
                    <>
                      <button className="btn btn--link" onClick={() => startEditing(category)}>
                        Editar
                      </button>
                      <button className="btn btn--link btn--danger" onClick={() => deleteCategory(category.id)}>
                        Apagar
                      </button>
                    </>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        <form className="categories-page__form" onSubmit={handleCreateCategory}>
          <input
            className="field"
            aria-label="Nova categoria"
            placeholder="Nova categoria"
            value={newCategoryName}
            onChange={(e) => setNewCategoryName(e.target.value)}
          />
          <button className="btn btn--primary" type="submit" disabled={creatingCategory || !newCategoryName.trim()}>
            {creatingCategory ? "A criar…" : "Criar categoria"}
          </button>
        </form>

      </section>

      <section className="section" aria-labelledby="rules-title">
        <h2 className="section__title" id="rules-title">Regras de categorização</h2>
        <p className="section__subtitle">
          Quando uma transação importada contém a palavra-chave, é atribuída automaticamente à categoria.
          Maiúsculas e acentos são ignorados.
        </p>

        {rules.length === 0 ? (
          <div className="empty-state categories-page__empty">
            <h3 className="empty-state__title">Ainda não há regras</h3>
            <p className="empty-state__text">
              Sem regras, os movimentos importados vão para "Outros". Por exemplo, a palavra-chave "continente" pode
              enviar as compras do supermercado para Alimentação.
            </p>
            <div className="empty-state__actions">
              <button type="button" className="btn btn--primary" onClick={() => keywordInputRef.current?.focus()}>
                Criar a primeira regra
              </button>
            </div>
          </div>
        ) : (
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
                    <button className="btn btn--link btn--danger" onClick={() => deleteRule(rule.id)}>
                      Apagar
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}

        <form className="categories-page__form" onSubmit={handleCreateRule}>
          <input
            ref={keywordInputRef}
            className="field"
            aria-label="Palavra-chave"
            placeholder="Palavra-chave"
            value={newRuleKeyword}
            onChange={(e) => setNewRuleKeyword(e.target.value)}
          />
          <select className="field" aria-label="Categoria da regra" value={newRuleCategoryId} onChange={(e) => setNewRuleCategoryId(e.target.value)}>
            <option value="">Categoria…</option>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </select>
          <button
            className="btn btn--primary"
            type="submit"
            disabled={creatingRule || !newRuleKeyword.trim() || !newRuleCategoryId}
          >
            {creatingRule ? "A criar…" : "Criar regra"}
          </button>
        </form>
      </section>
    </AppLayout>
  );
}
