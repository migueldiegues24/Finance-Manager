import { useId, useState, type FormEvent, type RefObject } from "react";
import { apiFetch } from "../api/client";
import { choiceFromStored, storedFromChoice, type ColorChoice } from "../utils/colorChoice";
import ColorPicker from "./ColorPicker";
import Modal from "./Modal";
import Notice from "./Notice";
import { apiFailure, toNotice, type NoticeContent } from "../api/errors";

interface CategoryDialogProps {
  open: boolean;
  // null = nova categoria; caso contrário, edita esta.
  category: { id: number; name: string; color?: string | null } | null;
  onClose: () => void;
  // Chamado depois de gravar; o diálogo fecha quando a lista estiver atualizada.
  onSaved: () => Promise<void>;
  returnFocusRef?: RefObject<HTMLElement | null>;
}

// Criar ou editar uma categoria. Montado com uma key nova a cada abertura,
// por isso o estado inicial vem sempre das props.
export default function CategoryDialog({ open, category, onClose, onSaved, returnFocusRef }: CategoryDialogProps) {
  const formId = useId();
  const inputId = useId();
  const [name, setName] = useState(category?.name ?? "");
  const [color, setColor] = useState<ColorChoice>(() => choiceFromStored(category?.color));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<NoticeContent | null>(null);
  const isEdit = category !== null;

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!name.trim() || saving) return;

    setSaving(true);
    setError(null);
    try {
      const res = await apiFetch(isEdit ? `/categories/${category.id}` : "/categories", {
        method: isEdit ? "PUT" : "POST",
        // O PUT substitui a categoria inteira: envia sempre nome e cor
        // (null = cor automática).
        body: JSON.stringify({ name: name.trim(), color: storedFromChoice(color) }),
      });
      if (!res.ok) {
        throw await apiFailure(res, isEdit ? "Não foi possível editar a categoria." : "Não foi possível criar a categoria.");
      }
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
      title={isEdit ? "Editar categoria" : "Nova categoria"}
      onClose={onClose}
      returnFocusRef={returnFocusRef}
      footer={
        <>
          <button type="button" className="btn btn--ghost" onClick={onClose}>
            Cancelar
          </button>
          <button type="submit" form={formId} className="btn btn--primary" disabled={saving || !name.trim()}>
            {saving ? "A gravar…" : "Gravar"}
          </button>
        </>
      }
    >
      <form id={formId} onSubmit={handleSubmit} noValidate>
        {error && <Notice tone="error" title={error.title} detail={error.detail} className="modal__status" />}
        <label className="field-label" htmlFor={inputId}>
          Nome
        </label>
        <input
          id={inputId}
          className="field"
          value={name}
          onChange={(e) => setName(e.target.value)}
          maxLength={100}
          autoComplete="off"
          required
        />
        <ColorPicker value={color} onChange={setColor} previewName={name.trim()} categoryId={category?.id ?? null} />
      </form>
    </Modal>
  );
}
