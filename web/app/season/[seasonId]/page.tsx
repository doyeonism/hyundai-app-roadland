import Link from "next/link";
import { notFound } from "next/navigation";
import Bar from "@/components/Bar";
import { getSeason } from "@/lib/api";

// 시즌 내용은 전부 서버 데이터. 시즌이 바뀌어도 이 파일은 그대로다.
export default async function SeasonPage({ params }: { params: Promise<{ seasonId: string }> }) {
  const { seasonId } = await params;
  const season = await getSeason(seasonId);
  if (!season) notFound();

  return (
    <main className="shell">
      <div className="row between">
        <h1>시즌 · {season.name}</h1>
        <Link href="/" className="muted">
          ‹ 오늘
        </Link>
      </div>
      <p className="muted">
        {season.startsAt} ~ {season.endsAt}
      </p>
      <section className="card">
        <p>{season.theme}</p>
      </section>
      <section className="card">
        <h2>주간 미션</h2>
        <div className="list">
          {season.weeklyMissions.map((m) => (
            <div key={m.id}>
              <div className="row between">
                <span>{m.text}</span>
                <span className="muted">{m.reward}</span>
              </div>
              <Bar label="진행" value={m.progress} max={m.goal} />
            </div>
          ))}
        </div>
      </section>
      <section className="card">
        <div className="row between">
          <h2>시즌패스</h2>
          <span className="pill">{season.passTier}단계</span>
        </div>
        <div className="list">
          {season.pass.map((p) => (
            <div key={p.tier} className="row between" style={{ opacity: p.tier <= season.passTier ? 1 : 0.6 }}>
              <span>{p.tier}단계</span>
              <span>무료: {p.free}</span>
              <span className="muted">💎 {p.paid}</span>
            </div>
          ))}
        </div>
      </section>
    </main>
  );
}
