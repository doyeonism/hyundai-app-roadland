import Link from "next/link";
import { getDex } from "@/lib/api";
import { AXES, AXIS_COLOR } from "@/lib/rules";

export default async function DexPage() {
  const dex = await getDex();
  const unlocked = dex.filter((f) => f.unlocked).length;

  return (
    <>
      <div className="row between">
        <h1>도감</h1>
        <span className="muted">
          {unlocked} / {dex.length}
        </span>
      </div>
      <p className="muted">주 형태 6 × 장식 5 × 진화 3단계. 거쳐 간 모습은 습관이 바뀌어도 남아요.</p>
      {AXES.map((a) => (
        <section key={a.id} className="card">
          <div className="row between">
            <h2 style={{ color: AXIS_COLOR[a.id] }}>{a.form} 붕붕이</h2>
            <span className="muted">{a.label}</span>
          </div>
          <div className="dex">
            {dex
              .filter((f) => f.main === a.id)
              .map((f) => (
                <Link key={f.id} href={`/dex/${f.id}`} className={f.unlocked ? "on" : undefined} aria-label={f.unlocked ? f.name : "아직 못 만난 모습"}>
                  {f.unlocked ? "🚗" : "?"}
                </Link>
              ))}
          </div>
        </section>
      ))}
    </>
  );
}
