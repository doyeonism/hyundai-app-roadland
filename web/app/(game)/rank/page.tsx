import Link from "next/link";
import { getCurrentSeasonId, getRanking } from "@/lib/api";
import type { RankRow } from "@/lib/types";

function Board({ title, note, rows, unit }: { title: string; note: string; rows: RankRow[]; unit: string }) {
  return (
    <section className="card">
      <h2>{title}</h2>
      <p className="muted">{note}</p>
      <div className="list">
        {rows.map((r) => (
          <div key={r.name} className="row between" style={r.me ? { fontWeight: 700, color: "var(--accent)" } : undefined}>
            <span>
              {r.rank}. {r.name}
              {r.me && " (나)"}
            </span>
            <span>
              {r.value}
              {unit}
            </span>
          </div>
        ))}
      </div>
    </section>
  );
}

export default async function RankPage() {
  const [tiles, smooth, seasonId] = await Promise.all([getRanking("tiles"), getRanking("smooth"), getCurrentSeasonId()]);
  return (
    <>
      <div className="row between">
        <h1>순위</h1>
        <Link href={`/season/${seasonId}`} className="pill">
          시즌 보상 보기
        </Link>
      </div>
      <Board title="우리 동네 타일 수" note="우리 집 타일이 있는 구 안에서만 셉니다." rows={tiles} unit="칸" />
      <Board title="이번 주 부드러운 운전왕" note="급제동과 급조향이 적을수록 높아요." rows={smooth} unit="점" />
    </>
  );
}
