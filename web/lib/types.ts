// 서버 API가 돌려줄 모양. 더미 백엔드와 실제 백엔드가 같은 타입을 쓴다.

export type Axis = "smooth" | "explore" | "eco" | "safe" | "steady" | "together";

export type AxisScores = Record<Axis, number>; // 0~100, 최근 14일

export type Stage = number; // 1부터. 단계 수와 진화 레벨은 lib/grades.ts

/** 진화할 때 정해져 다음 진화까지 유지되는 모습. */
export interface Form {
  main: Axis;
  sub: Axis;
}

export interface Bungbung {
  vehicleId: string;
  name: string;
  level: number;
  xp: number;
  xpToNext: number;
  stage: Stage;
  form: Form | null; // 진화 전에는 null
  axes: AxisScores;
  fullness: number; // 배부름 0~100
  mood: number; // 기분 0~100
  health: number; // 건강 0~100
  equipped: { color: string; hat: string | null; deco: string | null };
}

export interface Wallet {
  food: number;
  coins: number;
  gems: number;
  buildGauge: number; // 0~100
}

export interface Mission {
  id: string;
  text: string;
  progress: number;
  goal: number;
  reward: string;
}

export interface Trip {
  id: string;
  endedAt: string; // ISO
  from: string;
  to: string;
  km: number;
  hardBrakes: number;
  foodEarned: number;
  newTiles: number;
  passengers: number;
  gaugeGained: number;
  foodCapReached: boolean;
  completedMissionIds: string[];
  line: string; // 붕붕이 말풍선
}

export type TileOwner = { kind: "me" } | { kind: "rival"; name: string } | { kind: "npc"; name: string } | { kind: "none" };

export interface Tile {
  h3: string;
  owner: TileOwner;
  affection: number;
  protectedHome: boolean;
  building: { typeId: string; builtAt: string } | null;
  lastVisit: string | null;
  partner: string | null; // 제휴 거점 이름
}

export interface BuildingType {
  id: string;
  name: string;
  emoji: string;
  coinCost: number;
  coinsPerHour: number;
}

export interface DexForm {
  id: string; // `${main}-${sub}-${stage}`
  main: Axis;
  sub: Axis;
  stage: Stage;
  name: string;
  unlocked: boolean;
}

export interface RankRow {
  rank: number;
  name: string;
  value: number;
  me: boolean;
}

export interface Season {
  id: string;
  name: string;
  startsAt: string;
  endsAt: string;
  theme: string;
  weeklyMissions: Mission[];
  pass: { tier: number; free: string; paid: string }[];
  passTier: number;
}

export interface CosmeticItem {
  id: string;
  slot: "color" | "hat" | "deco";
  name: string;
  value: string;
  owned: boolean;
}
