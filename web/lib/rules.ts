import { GRADES, MAX_LEVEL, XP_PER_FOOD, xpToNext } from "./grades";
import type { Axis, AxisScores, Bungbung, Form, Stage } from "./types";

export { MAX_LEVEL, XP_PER_FOOD, xpToNext };

export const AXES: { id: Axis; label: string; form: string; look: string }[] = [
  { id: "smooth", label: "부드러움", form: "구름", look: "말랑한 몸, 떠다님" },
  { id: "explore", label: "탐험", form: "캠핑", look: "지붕 텐트, 큰 바퀴" },
  { id: "eco", label: "알뜰", form: "새싹", look: "잎사귀 장식, 초록 몸" },
  { id: "safe", label: "안전", form: "방패", look: "단단한 범퍼, 경광등" },
  { id: "steady", label: "꾸준함", form: "시계", look: "기차처럼 길쭉, 시계" },
  { id: "together", label: "함께", form: "버스", look: "창문이 많고 넓음" },
];

export const AXIS_COLOR: Record<Axis, string> = {
  smooth: "#7aa7ff",
  explore: "#e0913b",
  eco: "#4caf6a",
  safe: "#d9534f",
  steady: "#8b6fd6",
  together: "#e6b422",
};

/** 단계 번호는 1부터. 1단계는 진화 전 기본형. */
export function stageForLevel(level: number): Stage {
  let stage = 1;
  GRADES.forEach((g, i) => {
    if (level >= g.fromLevel) stage = i + 1;
  });
  return stage;
}

export function stageLabel(stage: Stage) {
  return GRADES[stage - 1]?.label ?? "";
}

/** 그 단계로 진화하는 레벨. */
export function evolveLevel(stage: Stage) {
  return GRADES[stage - 1]?.fromLevel ?? MAX_LEVEL;
}

/** 진화해서 만나는 단계들(도감에 실리는 단계). */
export const EVOLVED_STAGES: Stage[] = GRADES.map((_, i) => i + 1).slice(1);

export function nextEvolveLevel(level: number) {
  return GRADES.find((g) => g.fromLevel > level)?.fromLevel ?? null;
}

/** 0(기본형)~1(마지막 단계). 그림 크기에 쓴다. */
export function growth(stage: Stage) {
  return GRADES.length > 1 ? (stage - 1) / (GRADES.length - 1) : 1;
}

/** 가장 높은 축이 주 형태, 두 번째 축이 부 장식. */
export function topAxes(axes: AxisScores): [Axis, Axis] {
  const sorted = (Object.keys(axes) as Axis[]).sort((a, b) => axes[b] - axes[a]);
  return [sorted[0], sorted[1]];
}

export function axisInfo(id: Axis) {
  return AXES.find((a) => a.id === id)!;
}

export function formName(form: Form | null) {
  return form ? `${axisInfo(form.main).form} 붕붕이` : `${GRADES[0].label} 붕붕이`;
}

/**
 * 경험치를 더해 레벨과 단계를 올린다. 한 번에 여러 단계를 넘으면 넘은 단계를 모두 돌려준다.
 * 최고 레벨에서는 경험치 막대를 가득 채운 채 멈춘다.
 */
export function gainXp(b: Pick<Bungbung, "level" | "xp" | "xpToNext" | "stage">, xp: number): Stage[] {
  const passed: Stage[] = [];
  b.xp += xp;
  while (b.level < MAX_LEVEL && b.xp >= b.xpToNext) {
    b.xp -= b.xpToNext;
    b.level += 1;
    b.xpToNext = xpToNext(b.level);
    const stage = stageForLevel(b.level);
    if (stage > b.stage) {
      for (let s = b.stage + 1; s <= stage; s++) passed.push(s);
      b.stage = stage;
    }
  }
  if (b.level >= MAX_LEVEL) b.xp = b.xpToNext;
  return passed;
}

/** 최고 레벨까지 남은 경험치를 채우는 데 필요한 먹이 수. */
export function foodToMax(b: Pick<Bungbung, "level" | "xp">) {
  if (b.level >= MAX_LEVEL) return 0;
  let need = -b.xp;
  for (let l = b.level; l < MAX_LEVEL; l++) need += xpToNext(l);
  return Math.ceil(Math.max(0, need) / XP_PER_FOOD);
}
