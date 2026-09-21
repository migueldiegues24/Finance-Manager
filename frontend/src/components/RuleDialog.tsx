import { useId, useState, type FormEvent, type RefObject } from "react";
import { apiFetch } from "../api/client";
import Modal from "./Modal";
import Notice from "./Notice";
import { apiFailure, toNotice, type NoticeContent } from "../api/errors";

interface RuleDialogProps {
  open: boolean;
  categories: { id: number; name: string }[];
  onClose: () => void;
  onSaved: () => Promise<void>;
  returnFocusRef?: RefObject<HTMLElement | null>;
}

// Nova regra. Editar regras precisa de PUT /api/rules/{id} no backend
// (ver docs/backlog.md); por agora só se criam e apagam.
export default function RuleDialog({ open, categories, onClose, onSaved, returnFocusRef }: RuleDialogProps) {
  const formId = useId();
  const keywordId = useId();
  const categoryId = useId();
  const [keyword, setKeyword] = useState("");
  const [targetId, setTargetId] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<NoticeContent | null>(null);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!keyword.trim() || !targetId || saving) return;

    setSaving(true);
    setError(null);
    try {
      const res = await apiFetch("/rules", {
        method: "POST",
        body: JSON.stringify({ keyword: keyword.trim(), categoryId: Number(targetId) }),
      });
      if (!res.ok) throw await apiFailure(res, "Não foi possível criar a regra.");
      await onSaved();
      onClose();
    } catch (err) {
      setError(toNotice(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal
      open={open}
      title="Nova regra"
      onClose={onClose}
      returnFocusRef={returnFocusRef}
      footer={
        <>
          <button type="button" className="btn btn--ghost" onClick={onClose}>
            Cancelar
          </button>
          <button
            type="submit"
            form={formId}
            className="btn btn--primary"
            disabled={saving || !keyword.trim() || !targetId}
          >
            {saving ? "A gravar…" : "Gravar"}
          </button>
        </>
      }
    >
      <form id={formId} className="dialog-form" onSubmit={handleSubmit} noValidate>
        {error && <Notice tone="error" title={error.title} detail={error.detail} className="modal__status" />}
        <div>
          <label className="field-label" htmlFor={keywordId}>
            Palavra-chave
          </label>
          <input
            id={keywordId}
            className="field"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            autoComplete="off"
            required
          />
        </div>
        <div>
          <label className="field-label" htmlFor={categoryId}>
            Categoria
          </label>
          <select
            id={categoryId}
            className="field"
            value={targetId}
            onChange={(e) => setTargetId(e.target.value)}
            required
          >
            <option value="">Escolher…</option>
            {categories.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </div>
      </form>
    </Modal>
  );
}
