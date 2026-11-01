import { notFound } from "next/navigation";
import BungbungAvatar from "@/components/BungbungAvatar";
import CustomizePanel from "@/components/CustomizePanel";
import Bar from "@/components/Bar";
import { getBungbung, getItems, resolveShareCode } from "@/lib/api";

// 동승자용. 로그인 없이 링크 코드로만 들어오고, 보기와 꾸미기만 된다.
export default async function SharePage({ params }: { params: Promise<{ code: string }> }) {
  const { code } = await params;
  if (!(await resolveShareCode(code))) notFound();
  const [b, items] = await Promise.all([getBungbung(), getItems()]);

  return (
    <main className="shell">
      <h1>{b.name}랑 같이 타는 중</h1>
      <section className="card" style={{ alignItems: "center" }}>
        <BungbungAvatar b={b} size={220} />
        <div style={{ width: "100%" }}>
          <Bar label="기분" value={b.mood} />
        </div>
      </section>
      <CustomizePanel b={b} items={items} shareCode={code} />
    </main>
  );
}
