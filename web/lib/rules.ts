import type { Axis, AxisScores, Stage } from "./types";

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

export const STAGE_LABEL: Record<Stage, string> = { 1: "아기", 2: "어린이", 3: "청년", 4: "어른" };

export const EVOLVE_LEVELS = [10, 30, 60] as const;

export function stageForLevel(level: number): Stage {
  if (level >= 60) return 4;
  if (level >= 30) return 3;
  if (level >= 10) return 2;
  return 1;
}

export function xpToNext(level: number) {
  return 40 + level * 10;
}

/** 가장 높은 축이 주 형태, 두 번째 축이 부 장식. */
export function topAxes(axes: AxisScores): [Axis, Axis] {
  const sorted = (Object.keys(axes) as Axis[]).sort((a, b) => axes[b] - axes[a]);
  return [sorted[0], sorted[1]];
}

export function axisInfo(id: Axis) {
  return AXES.find((a) => a.id === id)!;
}

export function formName(main: Axis, stage: Stage) {
  return stage === 1 ? "아기 붕붕이" : `${axisInfo(main).form} 붕붕이`;
}

export const XP_PER_FOOD = 5;
