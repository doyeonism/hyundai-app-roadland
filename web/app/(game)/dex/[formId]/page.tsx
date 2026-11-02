import Link from "next/link";
import { notFound } from "next/navigation";
import BungbungAvatar from "@/components/BungbungAvatar";
import { getForm } from "@/lib/api";
import { AXIS_COLOR, axisInfo, evolveLevel, stageLabel } from "@/lib/rules";

export default async function FormPage({ params }: { params: Promise<{ formId: string }> }) {
  const { formId } = await params;
  const form = await getForm(formId);
  if (!form) notFound();

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
          <BungbungAvatar b={{ stage: form.stage, form: { main: form.main, sub: form.sub }, equipped: { color: AXIS_COLOR[form.main], hat: null, deco: null } }} size={220} />
        </div>
        <p className="muted">
          {stageLabel(form.stage)} 단계 · {axisInfo(form.main).look}
        </p>
      </section>
      <section className="card">
        <h2>만나는 법</h2>
        <p>
          <strong>{axisInfo(form.main).label}</strong> 축이 가장 높고 <strong>{axisInfo(form.sub).label}</strong> 축이 두 번째일 때 Lv.
          {evolveLevel(form.stage)}에 진화하면 열려요. 한 번에 여러 단계를 진화해도 지나간 단계가 모두 열려요.
        </p>
      </section>
    </>
  );
}
