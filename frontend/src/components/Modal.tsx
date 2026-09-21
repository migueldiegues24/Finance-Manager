import {
  useEffect,
  useId,
  useLayoutEffect,
  useRef,
  type KeyboardEvent,
  type MouseEvent,
  type ReactNode,
  type RefObject,
} from "react";
import { createPortal } from "react-dom";
import "./Modal.css";

const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

interface ModalProps {
  open: boolean;
  title: string;
  onClose: () => void;
  children?: ReactNode;
  // Texto curto por baixo do título, ligado por aria-describedby.
  description?: ReactNode;
  // Botões (ex.: Cancelar / Gravar), alinhados à direita.
  footer?: ReactNode;
  // Elemento que recebe o foco ao abrir; por omissão o primeiro focável.
  initialFocusRef?: RefObject<HTMLElement | null>;
  // Recebe o foco ao fechar se o elemento que abriu o diálogo já não existir
  // (ex.: o botão "Apagar" de uma linha que foi apagada).
  returnFocusRef?: RefObject<HTMLElement | null>;
}

// Diálogo modal acessível e reutilizável:
// - role="dialog", aria-modal e título associado (aria-labelledby);
// - foco preso lá dentro; ao fechar volta ao elemento que o abriu;
// - Esc e clique no fundo fecham;
// - o resto da página fica inert e sem scroll enquanto está aberto.
export default function Modal({
  open,
  title,
  onClose,
  children,
  description,
  footer,
  initialFocusRef,
  returnFocusRef,
}: ModalProps) {
  const titleId = useId();
  const descriptionId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);
  const pressedOnBackdrop = useRef(false);
  // Guardado numa ref para o efeito de abertura não depender de onClose.
  const onCloseRef = useRef(onClose);
  const returnFocusRefRef = useRef(returnFocusRef);
  useEffect(() => {
    onCloseRef.current = onClose;
    returnFocusRefRef.current = returnFocusRef;
  }, [onClose, returnFocusRef]);

  // Bloqueia o fundo (scroll e interação) e devolve o foco ao fechar.
  useLayoutEffect(() => {
    if (!open) return;
    const previouslyFocused = document.activeElement as HTMLElement | null;
    const root = document.getElementById("root");
    const body = document.body;
    const scrollbarWidth = window.innerWidth - document.documentElement.clientWidth;
    const previous = { overflow: body.style.overflow, paddingRight: body.style.paddingRight };

    body.style.overflow = "hidden";
    // Compensa a barra de scroll que desaparece, para o fundo não saltar.
    if (scrollbarWidth > 0) body.style.paddingRight = `${scrollbarWidth}px`;
    if (root) root.inert = true;

    return () => {
      body.style.overflow = previous.overflow;
      body.style.paddingRight = previous.paddingRight;
      if (root) root.inert = false;
      if (previouslyFocused?.isConnected) previouslyFocused.focus();
      else returnFocusRefRef.current?.current?.focus();
    };
  }, [open]);

  // Foco inicial.
  useEffect(() => {
    if (!open) return;
    const dialog = dialogRef.current;
    const target = initialFocusRef?.current ?? dialog?.querySelector<HTMLElement>(FOCUSABLE) ?? dialog;
    target?.focus();
  }, [open, initialFocusRef]);

  if (!open) return null;

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === "Escape") {
      event.stopPropagation();
      onCloseRef.current();
      return;
    }
    if (event.key !== "Tab") return;

    // Foco preso: Tab no último volta ao primeiro, Shift+Tab no primeiro vai ao último.
    const focusable = Array.from(dialogRef.current?.querySelectorAll<HTMLElement>(FOCUSABLE) ?? []);
    if (focusable.length === 0) {
      event.preventDefault();
      return;
    }
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  // Só fecha se o clique começar e acabar no fundo (arrastar uma seleção de
  // texto para fora do diálogo não o fecha).
  function handleBackdropMouseDown(event: MouseEvent<HTMLDivElement>) {
    pressedOnBackdrop.current = event.target === event.currentTarget;
  }

  function handleBackdropClick(event: MouseEvent<HTMLDivElement>) {
    if (pressedOnBackdrop.current && event.target === event.currentTarget) onCloseRef.current();
    pressedOnBackdrop.current = false;
  }

  return createPortal(
    <div className="modal-backdrop" onMouseDown={handleBackdropMouseDown} onClick={handleBackdropClick}>
      <div
        ref={dialogRef}
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={description ? descriptionId : undefined}
        tabIndex={-1}
        onKeyDown={handleKeyDown}
      >
        <div className="modal__header">
          <h2 className="modal__title" id={titleId}>
            {title}
          </h2>
          <button type="button" className="btn btn--link modal__close" onClick={() => onCloseRef.current()} aria-label="Fechar">
            ×
          </button>
        </div>
        {description && (
          <p className="modal__description" id={descriptionId}>
            {description}
          </p>
        )}
        {children && <div className="modal__body">{children}</div>}
        {footer && <div className="modal__footer">{footer}</div>}
      </div>
    </div>,
    document.body,
  );
}
