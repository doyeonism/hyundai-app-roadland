import { beforeEach, describe, expect, it } from "vitest";
import { feed, getBungbung, getCarHome, getDex, getWallet } from "./api";
import { GRADES, MAX_LEVEL } from "./grades";
import { EVOLVED_STAGES, xpToNext } from "./rules";

const g = globalThis as unknown as { __bbState?: unknown };
beforeEach(() => {
  delete g.__bbState;
});

async function setFood(n: number) {
  (await getWallet()).food = n;
}

describe("feed", () => {
  it("여러 단계를 한 번에 넘어도 지나간 단계의 도감이 모두 열린다", async () => {
    const b = await getBungbung();
    b.level = 1;
    b.xp = 0;
    b.xpToNext = xpToNext(1);
    b.stage = 1;
    b.form = null;
    await setFood(100_000);
    const r = await feed(100_000);
    expect(r.evolved).toBe(true);
    const { main, sub } = b.form!;
    const opened = (await getDex()).filter((f) => f.unlocked).map((f) => f.id);
    expect(opened.sort()).toEqual(EVOLVED_STAGES.map((s) => `${main}-${sub}-${s}`).sort());
  });

  it("진화 뒤 운전 습관이 바뀌어도 모습은 다음 진화까지 그대로다", async () => {
    const b = await getBungbung();
    b.xp = b.xpToNext - 5;
    b.level = GRADES[1].fromLevel - 1;
    await setFood(1);
    await feed(1);
    const form = { ...b.form! };
    b.axes[form.main] = 0;
    const home = await getCarHome();
    expect(home.bungbung.mainAxis).toBe(form.main);
    const dexForm = (await getDex()).find((f) => f.id === `${form.main}-${form.sub}-${b.stage}`);
    expect(dexForm?.unlocked).toBe(true);
  });

  it("최고 레벨에 닿을 만큼만 먹이를 쓰고 나머지는 남긴다", async () => {
    await setFood(1_000_000);
    const r = await feed(1_000_000);
    const b = await getBungbung();
    expect(b.level).toBe(MAX_LEVEL);
    expect(r.maxed).toBe(true);
    const left = (await getWallet()).food;
    expect(left).toBeGreaterThan(0);
    expect(await feed(left)).toEqual({ evolved: false, maxed: true });
    expect((await getWallet()).food).toBe(left);
  });

  it("차량 앱에 단계 이름과 크기를 보낸다", async () => {
    const home = await getCarHome();
    expect(home.bungbung.stageLabel).toBe(GRADES[home.bungbung.stage - 1].label);
    expect(home.bungbung.growth).toBeGreaterThanOrEqual(0);
    expect(home.bungbung.growth).toBeLessThanOrEqual(1);
  });
});
