import Link from "next/link";
import BungbungAvatar from "@/components/BungbungAvatar";
import Bar from "@/components/Bar";
import { FeedForm } from "@/components/forms";
import { logoutAction } from "@/app/actions";
import { getBungbung, getWallet, SHARE_CODE } from "@/lib/api";
import { AXES, AXIS_COLOR, MAX_LEVEL, axisInfo, formName, nextEvolveLevel, stageLabel, topAxes } from "@/lib/rules";

export default async function BungbungPage() {
  const [b, wallet] = await Promise.all([getBungbung(), getWallet()]);
  const [nextMain] = topAxes(b.axes);
  const nextEvolve = nextEvolveLevel(b.level);
  const maxed = b.level >= MAX_LEVEL;

  return (
    <>
      <h1>{b.name}</h1>
      <section className="card" style={{ alignItems: "center" }}>
        <BungbungAvatar b={b} size={220} />
        <div style={{ textAlign: "center" }}>
          <strong>
            Lv.{b.level} {formName(b.form)}
          </strong>
          <div className="muted">
            {stageLabel(b.stage)} 단계
            {b.form && ` · ${axisInfo(b.form.main).label} 주 형태 · ${axisInfo(b.form.sub).label} 장식`}
          </div>
          {nextEvolve && (
            <div className="muted">
              Lv.{nextEvolve}에 진화 · 지금이라면 {axisInfo(nextMain).form} 붕붕이
            </div>
          )}
          {maxed && <div className="muted">최고 레벨 달성!</div>}
        </div>
        <div style={{ width: "100%" }}>
          <Bar label="경험치" value={b.xp} max={b.xpToNext} />
        </div>
        <Link href="/bungbung/customize" className="btn ghost" style={{ width: "100%" }}>
          꾸미기
        </Link>
      </section>

      <FeedForm food={wallet.food} maxed={maxed} />

      <section className="card">
        <h2>상태</h2>
        <Bar label="배부름" value={b.fullness} />
        <Bar label="기분 (오늘 운전 품질)" value={b.mood} />
        <Bar label="건강 (연비)" value={b.health} />
      </section>

      <section className="card">
        <h2>운전 습관 6축</h2>
        <p className="muted">진화하는 순간 가장 높은 축이 주 형태, 두 번째 축이 장식이 돼요. 모습은 다음 진화까지 유지돼요.</p>
        {AXES.map((a) => (
          <Bar key={a.id} label={`${a.label} → ${a.form} 붕붕이`} value={b.axes[a.id]} color={AXIS_COLOR[a.id]} />
        ))}
      </section>

      <section className="card">
        <h2>동승자 초대</h2>
        <p className="muted">뒷자리 가족이 로그인 없이 붕붕이를 보고 꾸밀 수 있는 링크예요.</p>
        <Link href={`/share/${SHARE_CODE}`} className="btn ghost">
          /share/{SHARE_CODE}
        </Link>
      </section>

      <form action={logoutAction}>
        <button className="btn ghost" style={{ width: "100%" }}>
          로그아웃
        </button>
      </form>
    </>
  );
}
