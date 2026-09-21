import AuthForm from "../components/AuthForm";
import AuthLayout from "../components/AuthLayout";

export default function RegisterPage() {
  return (
    <AuthLayout>
      <AuthForm mode="register" />
    </AuthLayout>
  );
}
