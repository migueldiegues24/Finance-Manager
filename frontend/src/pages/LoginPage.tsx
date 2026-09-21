import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import AuthLayout from "../components/AuthLayout";
import { useAuth } from "../context/AuthContext";
import Notice from "../components/Notice";

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(email, password);
      navigate("/dashboard");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Algo correu mal. Tenta de novo.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthLayout>
      <form className="auth-form" onSubmit={handleSubmit}>
        <h2>Entrar</h2>
        <p className="auth-form__subtitle">Continua a acompanhar as tuas contas.</p>

        {error && <Notice tone="error" title={error} />}

        <div className="auth-field">
          <label className="field-label" htmlFor="email">Email</label>
          <input
            id="email"
            className="field"
            autoComplete="email"
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            required
          />
        </div>

        <div className="auth-field">
          <label className="field-label" htmlFor="password">Password</label>
          <input
            id="password"
            className="field"
            autoComplete="current-password"
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
          />
        </div>

        <button className="btn btn--primary btn--block auth-form__submit" type="submit" disabled={submitting}>
          {submitting ? "A entrar…" : "Entrar"}
        </button>

        <p className="auth-form__switch">
          Ainda não tens conta? <Link to="/register">Cria uma</Link>
        </p>
      </form>
    </AuthLayout>
  );
}
