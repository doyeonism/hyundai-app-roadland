import Link from "next/link";
import { notFound } from "next/navigation";
import BungbungAvatar from "@/components/BungbungAvatar";
import { getBungbung, getForm } from "@/lib/api";
import { AXIS_COLOR, STAGE_LABEL, axisInfo } from "@/lib/rules";
import type { AxisScores } from "@/lib/types";

export default async function FormPage({ params }: { params: Promise<{ formId: string }> }) {
  const { formId } = await params;
  const [form, b] = await Promise.all([getForm(formId), getBungbung()]);
  if (!form) notFound();

  // 도감 미리보기용 가짜 축 점수: 주 축과 부 축만 높게.
  const axes = Object.fromEntries(Object.keys(b.axes).map((k) => [k, 0])) as AxisScores;
  axes[form.main] = 100;
  axes[form.sub] = 50;

  return (
    <>
      <div className="row between">
        <h1>{form.unlocked ? form.name : "???"}</h1>
        <Link href="/dex" className="muted">
          ‹ 도감
        </Link>
      </div>
      <section className="card" style={{ alignItems: "center" }}>
        <div style={{ filter: form.unlocked ? undefined : "brightness(0) opacity(0.25)" }}>
          <BungbungAvatar b={{ stage: form.stage, axes, equipped: { color: AXIS_COLOR[form.main], hat: null, deco: null } }} size={220} />
        </div>
        <p className="muted">
          {STAGE_LABEL[form.stage]} 단계 · {axisInfo(form.main).look}
        </p>
      </section>
      <section className="card">
        <h2>만나는 법</h2>
        <p>
          <strong>{axisInfo(form.main).label}</strong> 축이 가장 높고 <strong>{axisInfo(form.sub).label}</strong> 축이 두 번째일 때
          {form.stage === 2 ? " Lv.10" : form.stage === 3 ? " Lv.30" : " Lv.60"}에 진화하면 열려요.
        </p>
      </section>
    </>
  );
}
