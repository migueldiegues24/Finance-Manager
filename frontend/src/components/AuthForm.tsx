import { useId, useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import Notice from "./Notice";
import PasswordField from "./PasswordField";

interface AuthFormProps {
  mode: "login" | "register";
  // Registo público desligado: o login mostra uma nota em vez do link "Criar conta".
  registrationClosed?: boolean;
}

const COPY = {
  login: {
    title: "Entrar",
    subtitle: "Entra com o teu email e password.",
    submit: "Entrar",
    submitting: "A entrar…",
    switchText: "Ainda não tens conta?",
    switchLink: "Criar conta",
    switchTo: "/register",
  },
  register: {
    title: "Criar conta",
    subtitle: "Só precisas de um email e de uma password.",
    submit: "Criar conta",
    submitting: "A criar conta…",
    switchText: "Já tens conta?",
    switchLink: "Entrar",
    switchTo: "/login",
  },
} as const;

// Formulário de login e de registo. Labels visíveis, autocomplete correto,
// foco no email ao abrir, botão desativado durante o pedido e erro curto
// (role="alert") junto do botão.
export default function AuthForm({ mode, registrationClosed = false }: AuthFormProps) {
  const { login, register } = useAuth();
  const navigate = useNavigate();
  const emailId = useId();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const copy = COPY[mode];

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (submitting) return;
    setError(null);
    setSubmitting(true);
    try {
      await (mode === "login" ? login(email, password) : register(email, password));
      navigate("/dashboard");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Algo correu mal. Tenta de novo.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="auth-form" onSubmit={handleSubmit}>
      <h1 className="auth-form__title">{copy.title}</h1>
      <p className="auth-form__subtitle">{copy.subtitle}</p>

      <div className="auth-field">
        <label className="field-label" htmlFor={emailId}>
          Email
        </label>
        <input
          id={emailId}
          className="field"
          type="email"
          autoComplete="email"
          inputMode="email"
          autoFocus
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          required
        />
      </div>

      <PasswordField
        value={password}
        onChange={setPassword}
        autoComplete={mode === "login" ? "current-password" : "new-password"}
        minLength={mode === "register" ? 12 : undefined}
        maxLength={mode === "register" ? 64 : undefined}
        hint={mode === "register" ? "Mínimo 12 caracteres. Evita passwords óbvias." : undefined}
      />

      {error && <Notice tone="error" title={error} className="auth-form__error" />}

      <button className="btn btn--primary btn--block auth-form__submit" type="submit" disabled={submitting}>
        {submitting ? copy.submitting : copy.submit}
      </button>

      <p className="auth-form__switch">
        {mode === "login" && registrationClosed ? (
          "O registo de novas contas está fechado."
        ) : (
          <>
            {copy.switchText} <Link to={copy.switchTo}>{copy.switchLink}</Link>
          </>
        )}
      </p>
    </form>
  );
}
