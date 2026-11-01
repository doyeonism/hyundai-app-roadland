import { redirect } from "next/navigation";
import { LinkForm } from "@/components/forms";
import { getSession } from "@/lib/session";

export default async function LinkPage() {
  const s = await getSession();
  if (!s.user) redirect("/login");
  return (
    <main className="shell">
      <h1>내 차와 연결</h1>
      <p className="muted">차에서 붕붕이 앱을 열고 첫 화면의 &lsquo;휴대폰 연결&rsquo;을 누르면 보이는 6자리 숫자를 입력하세요.</p>
      <LinkForm />
      <p className="muted">더미 연결입니다. 아무 6자리 숫자나 됩니다.</p>
    </main>
  );
}
