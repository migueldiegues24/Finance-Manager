import AuthLayout from "./AuthLayout";
import Notice from "./Notice";

interface SessionOfflineProps {
  retrying: boolean;
  onRetry: () => void;
}

// Mostrado no arranque quando não foi possível renovar a sessão por falta de
// rede ou erro do servidor. A sessão mantém-se: não se manda para o login.
export default function SessionOffline({ retrying, onRetry }: SessionOfflineProps) {
  return (
    <AuthLayout>
      <div className="auth-form">
        <h1 className="auth-form__title">Sem ligação</h1>
        <p className="auth-form__subtitle">
          Não foi possível contactar o servidor. A tua sessão continua guardada; tentamos outra vez
          quando a ligação voltar.
        </p>
        <Notice tone="attention" title="Verifica a ligação à internet e tenta de novo." />
        <button
          className="btn btn--primary btn--block auth-form__submit"
          type="button"
          onClick={onRetry}
          disabled={retrying}
        >
          {retrying ? "A tentar…" : "Tentar de novo"}
        </button>
      </div>
    </AuthLayout>
  );
}
