import { useRef, useState, type ReactNode, type RefObject } from "react";
import Modal from "./Modal";

interface ConfirmDialogProps {
  open: boolean;
  title: string;
  description?: ReactNode;
  confirmLabel: string;
  // Ação destrutiva: botão de confirmar em vermelho.
  destructive?: boolean;
  // Resolve quando terminar; se lançar, a mensagem aparece no diálogo.
  onConfirm: () => Promise<void>;
  onClose: () => void;
  returnFocusRef?: RefObject<HTMLElement | null>;
}

// Confirmação curta. O foco começa em "Cancelar", para Enter não apagar por engano.
export default function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel,
  destructive = false,
  onConfirm,
  onClose,
  returnFocusRef,
}: ConfirmDialogProps) {
  const cancelRef = useRef<HTMLButtonElement>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleConfirm() {
    setBusy(true);
    setError(null);
    try {
      await onConfirm();
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Algo correu mal. Tenta de novo.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal
      open={open}
      title={title}
      description={description}
      onClose={busy ? () => {} : onClose}
      initialFocusRef={cancelRef}
      returnFocusRef={returnFocusRef}
      footer={
        <>
          <button ref={cancelRef} type="button" className="btn btn--ghost" onClick={onClose} disabled={busy}>
            Cancelar
          </button>
          <button
            type="button"
            className={destructive ? "btn btn--destructive" : "btn btn--primary"}
            onClick={handleConfirm}
            disabled={busy}
          >
            {busy ? "A processar…" : confirmLabel}
          </button>
        </>
      }
    >
      {error && (
        <p className="status status--error modal__status" role="alert">
          {error}
        </p>
      )}
    </Modal>
  );
}
