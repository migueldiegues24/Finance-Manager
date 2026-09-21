import { useEffect, useId, useRef, useState, type KeyboardEvent } from "react";
import { useAuth } from "../context/AuthContext";
import { useTheme } from "../theme/themeContext";
import { initialsFromEmail } from "../utils/initials";
import type { ThemeMode } from "../utils/theme";
import "./UserMenu.css";

const THEME_OPTIONS: { mode: ThemeMode; label: string }[] = [
  { mode: "system", label: "Sistema" },
  { mode: "light", label: "Claro" },
  { mode: "dark", label: "Escuro" },
];

// Menu de utilizador (padrão "menu button"). Teclado: Enter/Espaço/↓ abrem
// no primeiro item, ↑ abre no último; no menu, ↑/↓ circulam, Home/End vão
// aos extremos, Enter/Espaço ativam, Esc fecha e devolve o foco ao botão,
// Tab fecha. Clique fora fecha. Escolher o tema deixa o menu aberto.
export default function UserMenu() {
  const { email, logout } = useAuth();
  const { mode, setMode } = useTheme();
  const [open, setOpen] = useState(false);
  const [leaving, setLeaving] = useState(false);
  const menuId = useId();
  const emailId = useId();
  const rootRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const itemRefs = useRef<(HTMLButtonElement | null)[]>([]);
  const pendingFocus = useRef<"first" | "last" | null>(null);

  const items = () => itemRefs.current.filter((el): el is HTMLButtonElement => el !== null && !el.disabled);

  // Foco no item pedido quando o menu acaba de abrir.
  useEffect(() => {
    if (!open || !pendingFocus.current) return;
    const list = items();
    (pendingFocus.current === "last" ? list[list.length - 1] : list[0])?.focus();
    pendingFocus.current = null;
  }, [open]);

  // Clique fora fecha, sem roubar o foco ao que foi clicado.
  useEffect(() => {
    if (!open) return;
    function handlePointerDown(event: PointerEvent) {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    }
    document.addEventListener("pointerdown", handlePointerDown);
    return () => document.removeEventListener("pointerdown", handlePointerDown);
  }, [open]);

  function openMenu(focus: "first" | "last") {
    pendingFocus.current = focus;
    setOpen(true);
  }

  function close(restoreFocus: boolean) {
    setOpen(false);
    if (restoreFocus) buttonRef.current?.focus();
  }

  function handleButtonKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
    if (event.key === "ArrowDown") {
      event.preventDefault();
      openMenu("first");
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      openMenu("last");
    }
  }

  function handleMenuKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const list = items();
    const index = list.indexOf(document.activeElement as HTMLButtonElement);
    let next: HTMLButtonElement | undefined;

    switch (event.key) {
      case "ArrowDown":
        next = list[(index + 1) % list.length];
        break;
      case "ArrowUp":
        next = list[(index - 1 + list.length) % list.length];
        break;
      case "Home":
        next = list[0];
        break;
      case "End":
        next = list[list.length - 1];
        break;
      case "Escape":
        event.preventDefault();
        close(true);
        return;
      case "Tab":
        // Deixa o Tab seguir para o próximo elemento da página.
        setOpen(false);
        return;
      default:
        return;
    }
    event.preventDefault();
    next?.focus();
  }

  async function handleLogout() {
    setLeaving(true);
    await logout();
  }

  let index = 0;
  const registerItem = (el: HTMLButtonElement | null, position: number) => {
    itemRefs.current[position] = el;
  };

  return (
    <div className="user-menu" ref={rootRef}>
      <button
        ref={buttonRef}
        type="button"
        className="user-menu__button"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={menuId}
        aria-label="Menu de utilizador"
        onClick={() => (open ? close(false) : openMenu("first"))}
        onKeyDown={handleButtonKeyDown}
      >
        <span aria-hidden="true">{initialsFromEmail(email)}</span>
      </button>

      {open && (
        <div className="user-menu__popover">
          {email && (
            <p className="user-menu__email" id={emailId}>
              {email}
            </p>
          )}
          <div
            id={menuId}
            className="user-menu__menu"
            role="menu"
            aria-label="Menu de utilizador"
            aria-describedby={email ? emailId : undefined}
            onKeyDown={handleMenuKeyDown}
          >
            <div className="user-menu__label" aria-hidden="true">
              Tema
            </div>
            <div role="group" aria-label="Tema">
              {THEME_OPTIONS.map((option) => {
                const position = index++;
                const checked = mode === option.mode;
                return (
                  <button
                    key={option.mode}
                    ref={(el) => registerItem(el, position)}
                    type="button"
                    role="menuitemradio"
                    aria-checked={checked}
                    tabIndex={-1}
                    className="user-menu__item"
                    onClick={() => setMode(option.mode)}
                  >
                    <span className="user-menu__check" aria-hidden="true">
                      {checked ? "✓" : ""}
                    </span>
                    {option.label}
                  </button>
                );
              })}
            </div>
            <div role="separator" className="user-menu__separator" />
            <button
              ref={(el) => registerItem(el, THEME_OPTIONS.length)}
              type="button"
              role="menuitem"
              tabIndex={-1}
              className="user-menu__item"
              onClick={handleLogout}
              disabled={leaving}
            >
              <span className="user-menu__check" aria-hidden="true" />
              {leaving ? "A sair…" : "Sair"}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
