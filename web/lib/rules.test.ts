import { describe, expect, it } from "vitest";
import { GRADES, MAX_LEVEL, XP_PER_FOOD } from "./grades";
import { EVOLVED_STAGES, foodToMax, gainXp, growth, nextEvolveLevel, stageForLevel, xpToNext } from "./rules";

// 등급 값은 grades.ts에서 바뀔 수 있으니 숫자를 직접 쓰지 않고 GRADES에서 읽는다.
const lastStage = GRADES.length;
const fresh = (level = 1) => ({ level, xp: 0, xpToNext: xpToNext(level), stage: stageForLevel(level) });
const xpBetween = (from: number, to: number) => {
  let sum = 0;
  for (let l = from; l < to; l++) sum += xpToNext(l);
  return sum;
};

describe("등급 설정", () => {
  it("진화 레벨이 오름차순이고 최고 레벨 안에 있다", () => {
    expect(GRADES[0].fromLevel).toBe(1);
    for (let i = 1; i < GRADES.length; i++) expect(GRADES[i].fromLevel).toBeGreaterThan(GRADES[i - 1].fromLevel);
    expect(GRADES[GRADES.length - 1].fromLevel).toBeLessThanOrEqual(MAX_LEVEL);
  });

  it("레벨 경계마다 단계가 바뀐다", () => {
    GRADES.forEach((g, i) => {
      expect(stageForLevel(g.fromLevel)).toBe(i + 1);
      if (g.fromLevel > 1) expect(stageForLevel(g.fromLevel - 1)).toBe(i);
    });
    expect(EVOLVED_STAGES).toEqual(GRADES.slice(1).map((_, i) => i + 2));
  });

  it("다음 진화 레벨과 그림 크기", () => {
    expect(nextEvolveLevel(1)).toBe(GRADES[1]?.fromLevel ?? null);
    expect(nextEvolveLevel(MAX_LEVEL)).toBeNull();
    expect(growth(1)).toBe(0);
    expect(growth(lastStage)).toBe(1);
  });
});

describe("gainXp", () => {
  it("한 단계 진화하면 그 단계 하나를 돌려준다", () => {
    const b = fresh(GRADES[1].fromLevel - 1);
    expect(gainXp(b, b.xpToNext)).toEqual([2]);
    expect(b.level).toBe(GRADES[1].fromLevel);
    expect(b.stage).toBe(2);
  });

  it("한 번에 여러 단계를 넘으면 지나간 단계를 모두 돌려준다", () => {
    const b = fresh(1);
    const passed = gainXp(b, xpBetween(1, GRADES[lastStage - 1].fromLevel));
    expect(passed).toEqual(EVOLVED_STAGES);
    expect(b.stage).toBe(lastStage);
  });

  it("진화하지 않으면 빈 배열", () => {
    const b = fresh(1);
    expect(gainXp(b, 1)).toEqual([]);
    expect(b).toMatchObject({ level: 1, xp: 1 });
  });

  it("최고 레벨에서 멈추고 경험치 막대는 가득 찬다", () => {
    const b = fresh(1);
    gainXp(b, xpBetween(1, MAX_LEVEL) * 3);
    expect(b.level).toBe(MAX_LEVEL);
    expect(b.xp).toBe(b.xpToNext);
    expect(gainXp(b, 1000)).toEqual([]);
    expect(b.level).toBe(MAX_LEVEL);
  });

  it("foodToMax만큼 주면 정확히 최고 레벨이 된다", () => {
    const b = { ...fresh(5), xp: 12 };
    const food = foodToMax(b);
    gainXp(b, food * XP_PER_FOOD);
    expect(b.level).toBe(MAX_LEVEL);
    expect(foodToMax(b)).toBe(0);
  });
});
