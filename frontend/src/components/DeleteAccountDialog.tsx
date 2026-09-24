import { useId, useState, type FormEvent, type RefObject } from "react";
import { deleteAccount } from "../api/account";
import { toNotice, type NoticeContent } from "../api/errors";
import Modal from "./Modal";
import Notice from "./Notice";
import PasswordField from "./PasswordField";

interface DeleteAccountDialogProps {
  open: boolean;
  onClose: () => void;
  // Conta apagada no servidor: quem chama termina a sessão local.
  onDeleted: () => void;
  returnFocusRef?: RefObject<HTMLElement | null>;
}

// Confirmação para apagar a conta, com a password atual. O foco começa no
// campo da password (o primeiro campo), nunca no botão de apagar.
export default function DeleteAccountDialog({ open, onClose, onDeleted, returnFocusRef }: DeleteAccountDialogProps) {
  const formId = useId();
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<NoticeContent | null>(null);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!password || busy) return;

    setBusy(true);
    setError(null);
    try {
      await deleteAccount(password);
      onDeleted();
    } catch (err) {
      setError(toNotice(err));
      setBusy(false);
    }
  }

  return (
    <Modal
      open={open}
      title="Apagar a conta?"
      description="Apaga a conta e todos os dados: categorias, regras, importações e transações. Não dá para desfazer."
      onClose={busy ? () => {} : onClose}
      returnFocusRef={returnFocusRef}
      footer={
        <>
          <button type="button" className="btn btn--ghost" onClick={onClose} disabled={busy}>
            Cancelar
          </button>
          <button type="submit" form={formId} className="btn btn--destructive" disabled={busy || !password}>
            {busy ? "A apagar…" : "Apagar conta"}
          </button>
        </>
      }
    >
      <form id={formId} onSubmit={handleSubmit} noValidate>
        {error && <Notice tone="error" title={error.title} detail={error.detail} className="modal__status" />}
        <PasswordField
          label="Password atual"
          value={password}
          onChange={setPassword}
          autoComplete="current-password"
          hint="Para confirmar que és tu."
        />
      </form>
    </Modal>
  );
}
