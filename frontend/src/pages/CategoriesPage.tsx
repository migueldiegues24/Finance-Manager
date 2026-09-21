import { useCallback, useEffect, useRef, useState } from "react";
import { apiFetch } from "../api/client";
import AppLayout from "../components/AppLayout";
import CategoryDialog from "../components/CategoryDialog";
import ConfirmDialog from "../components/ConfirmDialog";
import { categoryStyle } from "../utils/categoryColor";
import "./CategoriesPage.css";
import Notice from "../components/Notice";
import { AppError, apiFailure, toNotice, type NoticeContent } from "../api/errors";

interface Category {
  id: number;
  name: string;
  defaultCategory: boolean;
}

async function fetchCategories(): Promise<Category[]> {
  const res = await apiFetch("/categories");
  if (!res.ok) throw new AppError("Não foi possível carregar as categorias.");
  return res.json();
}

// Diálogo de criar/editar; key muda a cada abertura para recomeçar o formulário.
interface DialogState {
  key: number;
  category: Category | null;
}

export default function CategoriesPage() {
  const [categories, setCategories] = useState<Category[] | null>(null);
  const [error, setError] = useState<NoticeContent | null>(null);
  const [dialog, setDialog] = useState<DialogState | null>(null);
  const [toDelete, setToDelete] = useState<Category | null>(null);
  const newButtonRef = useRef<HTMLButtonElement>(null);

  // Recarrega depois de gravar ou apagar; os diálogos esperam por isto
  // antes de fechar, para a lista já estar atualizada.
  const reload = useCallback(async () => {
    setCategories(await fetchCategories());
  }, []);

  useEffect(() => {
    let cancelled = false;
    fetchCategories()
      .then((data) => {
        if (!cancelled) setCategories(data);
      })
      .catch((err) => {
        if (!cancelled) setError(toNotice(err));
      });
    return () => {
      cancelled = true;
    };
  }, []);

  function openDialog(category: Category | null) {
    setDialog({ key: Date.now(), category });
  }

  async function deleteCategory(category: Category) {
    const res = await apiFetch(`/categories/${category.id}`, { method: "DELETE" });
    if (!res.ok) throw await apiFailure(res, "Não foi possível apagar a categoria.");
    await reload();
  }

  return (
    <AppLayout>
      <header className="page-header page-header--with-action">
        <div>
          <p className="page-header__eyebrow">Configuração</p>
          <h1 className="page-header__title">Categorias</h1>
          <p className="page-header__subtitle">A categoria protegida não pode ser alterada.</p>
        </div>
        <button ref={newButtonRef} type="button" className="btn btn--primary" onClick={() => openDialog(null)}>
          Nova categoria
        </button>
      </header>

      {error && <Notice tone="error" title={error.title} detail={error.detail} />}
      {categories === null && !error && <Notice title="A carregar…" />}

      {categories && (
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
                  {category.defaultCategory ? (
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
                  ) : (
                    <>
                      <button
                        type="button"
                        className="btn btn--link"
                        onClick={() => openDialog(category)}
                        aria-label={`Editar ${category.name}`}
                      >
                        Editar
                      </button>
                      <button
                        type="button"
                        className="btn btn--link btn--danger"
                        onClick={() => setToDelete(category)}
                        aria-label={`Apagar ${category.name}`}
                      >
                        Apagar
                      </button>
                    </>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {dialog && (
        <CategoryDialog
          key={dialog.key}
          open
          category={dialog.category}
          onClose={() => setDialog(null)}
          onSaved={reload}
          returnFocusRef={newButtonRef}
        />
      )}

      <ConfirmDialog
        open={toDelete !== null}
        title="Apagar a categoria?"
        description={`Os movimentos passam para ${categories?.find((c) => c.defaultCategory)?.name ?? "Sem Categoria"}.`}
        confirmLabel="Apagar"
        destructive
        onConfirm={() => deleteCategory(toDelete!)}
        onClose={() => setToDelete(null)}
        returnFocusRef={newButtonRef}
      />
    </AppLayout>
  );
}
