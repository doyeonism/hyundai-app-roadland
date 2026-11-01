import Link from "next/link";
import BungbungAvatar from "@/components/BungbungAvatar";
import Bar from "@/components/Bar";
import { FeedForm } from "@/components/forms";
import { logoutAction } from "@/app/actions";
import { getBungbung, getWallet, SHARE_CODE } from "@/lib/api";
import { AXES, AXIS_COLOR, EVOLVE_LEVELS, STAGE_LABEL, axisInfo, formName, topAxes } from "@/lib/rules";

export default async function BungbungPage() {
  const [b, wallet] = await Promise.all([getBungbung(), getWallet()]);
  const [main, sub] = topAxes(b.axes);
  const nextEvolve = EVOLVE_LEVELS.find((l) => l > b.level);

  return (
    <>
      <h1>{b.name}</h1>
      <section className="card" style={{ alignItems: "center" }}>
        <BungbungAvatar b={b} size={220} />
        <div style={{ textAlign: "center" }}>
          <strong>
            Lv.{b.level} {formName(main, b.stage)}
          </strong>
          <div className="muted">
            {STAGE_LABEL[b.stage]} 단계 · {axisInfo(main).label} 주 형태 · {axisInfo(sub).label} 장식
          </div>
          {nextEvolve && <div className="muted">Lv.{nextEvolve}에 진화</div>}
        </div>
        <div style={{ width: "100%" }}>
          <Bar label="경험치" value={b.xp} max={b.xpToNext} />
        </div>
        <Link href="/bungbung/customize" className="btn ghost" style={{ width: "100%" }}>
          꾸미기
        </Link>
      </section>

      <FeedForm food={wallet.food} />

      <section className="card">
        <h2>상태</h2>
        <Bar label="배부름" value={b.fullness} />
        <Bar label="기분 (오늘 운전 품질)" value={b.mood} />
        <Bar label="건강 (연비)" value={b.health} />
      </section>

      <section className="card">
        <h2>운전 습관 6축</h2>
        <p className="muted">최근 14일. 가장 높은 축이 다음 진화 모습을 정해요.</p>
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
