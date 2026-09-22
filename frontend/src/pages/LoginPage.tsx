import AuthForm from "../components/AuthForm";
import AuthLayout from "../components/AuthLayout";
import { useRegistrationEnabled } from "../hooks/useRegistrationEnabled";

export default function LoginPage() {
  const registrationEnabled = useRegistrationEnabled();

  return (
    <AuthLayout>
      <AuthForm mode="login" registrationClosed={registrationEnabled === false} />
    </AuthLayout>
  );
}
