import Link from "next/link";
import { notFound } from "next/navigation";
import BungbungAvatar from "@/components/BungbungAvatar";
import { getBungbung, getMissions, getTrip } from "@/lib/api";
import { requirePlayer } from "@/lib/session";

// 휴대폰 푸시 "붕붕이가 오늘 N칸을 먹었어요"가 여는 화면. 탭 없이 바로 보인다.
export default async function TripPage({ params }: { params: Promise<{ tripId: string }> }) {
  await requirePlayer();
  const { tripId } = await params;
  const [trip, b, missions] = await Promise.all([getTrip(tripId), getBungbung(), getMissions()]);
  if (!trip) notFound();
  const done = missions.filter((m) => trip.completedMissionIds.includes(m.id));

  return (
    <main className="shell">
      <div className="row between">
        <h1>도착 정산</h1>
        <Link href="/" className="muted">
          닫기
        </Link>
      </div>
      <p className="muted">
        {trip.from} → {trip.to} · {new Date(trip.endedAt).toLocaleString("ko-KR", { dateStyle: "short", timeStyle: "short" })}
      </p>
      <section className="card" style={{ alignItems: "center" }}>
        <BungbungAvatar b={b} size={180} />
        <p className="bubble">{trip.line}</p>
      </section>
      <section className="card">
        <div className="grid2">
          <div>
            <div className="big">+{trip.newTiles}</div>
            <div className="muted">새 타일</div>
          </div>
          <div>
            <div className="big">+{trip.foodEarned}</div>
            <div className="muted">먹이</div>
          </div>
          <div>
            <div className="big">{trip.km}</div>
            <div className="muted">km</div>
          </div>
          <div>
            <div className="big">{trip.hardBrakes}</div>
            <div className="muted">급제동</div>
          </div>
        </div>
        <div className="muted">건물 게이지 +{trip.gaugeGained}%</div>
        {trip.passengers > 0 && <div className="pill">동승자 보너스 ×{trip.passengers}</div>}
        {trip.foodCapReached && <div className="pill">오늘 먹이 가득! 이후 운전은 도감·탐험에만 반영돼요</div>}
      </section>
      {done.length > 0 && (
        <section className="card">
          <h2>완료한 미션</h2>
          {done.map((m) => (
            <div key={m.id} className="row between">
              <span>✅ {m.text}</span>
              <span className="muted">{m.reward}</span>
            </div>
          ))}
        </section>
      )}
      <div className="grid2">
        <Link href="/bungbung" className="btn">
          먹이 주러 가기
        </Link>
        <Link href="/map" className="btn ghost">
          지도 보기
        </Link>
      </div>
    </main>
  );
}
