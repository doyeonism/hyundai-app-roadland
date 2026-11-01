import Link from "next/link";
import BungbungAvatar from "@/components/BungbungAvatar";
import Bar from "@/components/Bar";
import { CollectButton } from "@/components/forms";
import { getBungbung, getCurrentSeasonId, getMissions, getSeason, getToday, getWallet, pendingCoins } from "@/lib/api";
import { formName, topAxes } from "@/lib/rules";

export default async function TodayPage() {
  const [b, wallet, today, missions, pending, seasonId] = await Promise.all([
    getBungbung(),
    getWallet(),
    getToday(),
    getMissions(),
    pendingCoins(),
    getCurrentSeasonId(),
  ]);
  const season = await getSeason(seasonId);
  const latest = today.trips[0];

  return (
    <>
      <div className="row between">
        <h1>오늘</h1>
        {season && (
          <Link href={`/season/${season.id}`} className="pill">
            시즌 {season.name}
          </Link>
        )}
      </div>

      <section className="card">
        <div className="row">
          <BungbungAvatar b={b} size={110} />
          <div style={{ flex: 1 }}>
            <strong>{b.name}</strong>
            <div className="muted">
              Lv.{b.level} {formName(topAxes(b.axes)[0], b.stage)}
            </div>
            {latest && <p className="bubble" style={{ marginTop: 8 }}>{latest.line}</p>}
          </div>
        </div>
        <div className="grid3">
          <div>
            <div className="big">{today.km.toFixed(0)}</div>
            <div className="muted">km</div>
          </div>
          <div>
            <div className="big">{today.newTiles}</div>
            <div className="muted">새 타일</div>
          </div>
          <div>
            <div className="big">{today.hardBrakes}</div>
            <div className="muted">급제동</div>
          </div>
        </div>
      </section>

      <section className="card">
        <h2>재화</h2>
        <div className="grid3">
          <div>🍙 먹이 {wallet.food}</div>
          <div>🪙 코인 {wallet.coins}</div>
          <div>💎 보석 {wallet.gems}</div>
        </div>
        <Bar label="건물 게이지" value={wallet.buildGauge} />
        {wallet.buildGauge >= 100 && (
          <Link href="/map" className="btn ghost">
            게이지가 찼어요. 지도에서 건물 짓기
          </Link>
        )}
      </section>

      <CollectButton coins={pending.coins} perHour={pending.perHour} />

      <section className="card">
        <h2>오늘의 미션</h2>
        <div className="list">
          {missions.map((m) => (
            <div key={m.id}>
              <div className="row between">
                <span>{m.progress >= m.goal ? "✅" : "⬜"} {m.text}</span>
                <span className="muted">{m.reward}</span>
              </div>
              <Bar label="진행" value={m.progress} max={m.goal} />
            </div>
          ))}
        </div>
      </section>

      <section className="card">
        <h2>최근 주행</h2>
        <div className="list">
          {today.trips.map((t) => (
            <Link key={t.id} href={`/trips/${t.id}`} className="row between">
              <span>
                {t.from} → {t.to}
              </span>
              <span className="muted">
                {t.km}km · 먹이 +{t.foodEarned} ›
              </span>
            </Link>
          ))}
          {today.trips.length === 0 && <p className="muted">오늘은 아직 주행이 없어요.</p>}
        </div>
      </section>
    </>
  );
}
