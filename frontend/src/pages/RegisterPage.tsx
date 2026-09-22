import { Link } from "react-router-dom";
import AuthForm from "../components/AuthForm";
import AuthLayout from "../components/AuthLayout";
import Notice from "../components/Notice";
import { useRegistrationEnabled } from "../hooks/useRegistrationEnabled";

export default function RegisterPage() {
  const registrationEnabled = useRegistrationEnabled();

  return (
    <AuthLayout>
      {registrationEnabled === false ? (
        <div className="auth-form">
          <h1 className="auth-form__title">Criar conta</h1>
          <Notice title="O registo de novas contas está desativado." />
          <p className="auth-form__switch">
            Já tens conta? <Link to="/login">Entrar</Link>
          </p>
        </div>
      ) : (
        <AuthForm mode="register" />
      )}
    </AuthLayout>
  );
}
