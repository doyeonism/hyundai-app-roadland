import Link from "next/link";
import BungbungAvatar from "@/components/BungbungAvatar";
import CustomizePanel from "@/components/CustomizePanel";
import { getBungbung, getItems } from "@/lib/api";

export default async function CustomizePage() {
  const [b, items] = await Promise.all([getBungbung(), getItems()]);
  return (
    <>
      <div className="row between">
        <h1>꾸미기</h1>
        <Link href="/bungbung" className="muted">
          ‹ 붕붕이
        </Link>
      </div>
      <section className="card" style={{ alignItems: "center" }}>
        <BungbungAvatar b={b} size={200} />
      </section>
      <CustomizePanel b={b} items={items} />
      <p className="muted">🔒 아이템은 시즌패스·미니게임 보상으로 열려요.</p>
    </>
  );
}
