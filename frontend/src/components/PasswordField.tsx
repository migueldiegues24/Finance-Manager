import { useId, useState } from "react";
import "./PasswordField.css";

interface PasswordFieldProps {
  // Rótulo visível; por omissão "Password".
  label?: string;
  value: string;
  onChange: (value: string) => void;
  autoComplete: "current-password" | "new-password";
  minLength?: number;
  maxLength?: number;
  // Texto de ajuda por baixo do campo, ligado por aria-describedby.
  hint?: string;
}

// Password com um botão de alternância "Mostrar password". O nome é fixo
// e o estado vem de aria-pressed (padrão de toggle button); visualmente, o
// estado ligado fica sublinhado e a própria password passa a ver-se.
export default function PasswordField({
  label = "Password",
  value,
  onChange,
  autoComplete,
  minLength,
  maxLength,
  hint,
}: PasswordFieldProps) {
  const inputId = useId();
  const hintId = useId();
  const [visible, setVisible] = useState(false);

  return (
    <div className="auth-field">
      <label className="field-label" htmlFor={inputId}>
        {label}
      </label>
      <div className="password-field">
        <input
          id={inputId}
          className="field password-field__input"
          type={visible ? "text" : "password"}
          autoComplete={autoComplete}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          minLength={minLength}
          maxLength={maxLength}
          aria-describedby={hint ? hintId : undefined}
          required
        />
        <button
          type="button"
          className="btn btn--link password-field__toggle"
          aria-pressed={visible}
          aria-controls={inputId}
          onClick={() => setVisible((v) => !v)}
        >
          Mostrar<span className="visually-hidden"> password</span>
        </button>
      </div>
      {hint && (
        <p className="status__detail auth-field__hint" id={hintId}>
          {hint}
        </p>
      )}
    </div>
  );
}
