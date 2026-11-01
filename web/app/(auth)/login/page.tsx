import { LoginForm } from "@/components/forms";

export default function LoginPage() {
  return (
    <main className="shell">
      <h1>붕붕이</h1>
      <p className="muted">내 차가 곧 캐릭터. 휴대폰에서 붕붕이를 키우고 땅을 꾸며요.</p>
      <LoginForm />
      <p className="muted">더미 로그인입니다. 아무 닉네임이나 넣으면 됩니다.</p>
    </main>
  );
}
