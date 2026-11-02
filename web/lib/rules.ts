import type { Tile, TripInput } from "./types";

// 게임 수치. 밸런스를 바꿀 때는 이 파일만 고친다. 차량 앱의 주행 중 예상 크레딧(Rules.kt)도 같은 값을 쓴다.
export const RULES = {
  /** 안전하게 지나간 타일 한 칸의 크레딧. */
  tileCredit: 10,
  /** 에코 모드·크루즈로 지나간 타일의 추가 비율. */
  assistBonus: 0.5,
  /** 차선 변경 때 깜빡이 사용률이 이 이상이면 매너 보너스. */
  mannerSignalRate: 0.8,
  mannerBonus: 0.2,
  /** 하루 주행으로 받는 크레딧 상한. 오래 달리는 것이 최적 전략이 되지 않게 한다. */
  dailyDriveCap: 400,
  buyBaseCost: 30,
  buyCostPerOwned: 5,
  upgradeCostPerLevel: 40,
  maxTileLevel: 5,
  incomePerLevel: 4, // 시간당
  /** 최근 7일 중 이 일수 이상 지나간 타일은 홈 루트. */
  homeRouteDays: 3,
  homeRouteBonus: 1.5,
  /** 방치 수익이 쌓이는 최대 시간. 정기적인 탑승과 수령을 유도한다. */
  idleCapHours: 12,
  /** 안전 연속 기록으로 치는 점수. */
  safeStreakScore: 90,
  /** 기록 검증: 이보다 빠른 평균 속도나 거리 대비 너무 많은 타일은 보상하지 않는다. */
  maxAvgKph: 200,
  maxTilesPerKm: 12,
} as const;

const HOUR = 3_600_000;

/** 서울 기준 날짜 YYYY-MM-DD. */
export function seoulDay(at: Date | string) {
  return new Date(at).toLocaleDateString("sv-SE", { timeZone: "Asia/Seoul" });
}

export function daysAgo(day: string, n: number) {
  const d = new Date(`${day}T00:00:00+09:00`);
  return seoulDay(new Date(d.getTime() - n * 24 * HOUR));
}

export function validateTrip(t: TripInput) {
  const hours = (new Date(t.endedAt).getTime() - new Date(t.startedAt).getTime()) / HOUR;
  if (!(hours > 0) || t.km < 0) return false;
  if (t.km / hours > RULES.maxAvgKph) return false;
  if (t.tiles.length > t.km * RULES.maxTilesPerKm + 3) return false;
  return t.safetyScore >= 0 && t.safetyScore <= 100;
}

/** 일일 상한 적용 전 이번 주행의 크레딧. 같은 타일을 여러 번 지나도 한 번만 친다. */
export function tripCredits(t: Pick<TripInput, "tiles" | "laneChanges" | "signaledLaneChanges">) {
  const seen = new Set<string>();
  let clean = 0;
  let assistBonus = 0;
  let excluded = 0;
  for (const s of t.tiles) {
    if (seen.has(s.id)) continue;
    seen.add(s.id);
    if (s.hardBrake || s.warning) {
      excluded++;
      continue;
    }
    clean += RULES.tileCredit;
    if (s.assisted) assistBonus += RULES.tileCredit * RULES.assistBonus;
  }
  const mannered = t.laneChanges > 0 && t.signaledLaneChanges / t.laneChanges >= RULES.mannerSignalRate;
  const mannerBonus = mannered ? Math.round((clean + assistBonus) * RULES.mannerBonus) : 0;
  return { clean, assistBonus: Math.round(assistBonus), mannerBonus, excluded, total: clean + Math.round(assistBonus) + mannerBonus };
}

export function isHomeRoute(tile: Pick<Tile, "visitDays">, today: string) {
  const since = daysAgo(today, 6);
  return tile.visitDays.filter((d) => d >= since && d <= today).length >= RULES.homeRouteDays;
}

export function incomePerHour(tile: Pick<Tile, "level" | "visitDays">, today: string) {
  if (tile.level <= 0) return 0;
  const base = RULES.incomePerLevel * tile.level;
  return Math.round(isHomeRoute(tile, today) ? base * RULES.homeRouteBonus : base);
}

/** 마지막 수령 뒤 쌓인 수익. 상한 시간까지만 쌓인다. */
export function pendingIncome(tile: Pick<Tile, "level" | "visitDays" | "collectedAt">, now: Date) {
  if (tile.level <= 0 || !tile.collectedAt) return 0;
  const hours = Math.min(RULES.idleCapHours, Math.max(0, (now.getTime() - new Date(tile.collectedAt).getTime()) / HOUR));
  return Math.floor(incomePerHour(tile, seoulDay(now)) * hours);
}

export function buyCost(ownedCount: number) {
  return RULES.buyBaseCost + RULES.buyCostPerOwned * ownedCount;
}

export function upgradeCost(level: number) {
  return level >= 1 && level < RULES.maxTileLevel ? RULES.upgradeCostPerLevel * level : null;
}
