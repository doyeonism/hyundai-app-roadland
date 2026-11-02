import "server-only";
import { COMMUTE, createSeed, sampleRoute, type DummyState } from "./dummy/seed";
import { buyCost, incomePerHour, isHomeRoute, pendingIncome, RULES, seoulDay, tripCredits, upgradeCost, validateTrip } from "./rules";
import type { Records, Tile, TileView, Trip, TripInput } from "./types";

// 더미 백엔드. 라우트는 이 파일의 함수만 부른다.
// 실제 서버가 생기면 함수 안을 DB 호출로 바꾸고 시그니처는 그대로 둔다.
// 상태는 서버 메모리에 있어 dev 서버를 재시작하면 초기화된다.

const g = globalThis as unknown as { __rlState?: DummyState };
function db(): DummyState {
  return (g.__rlState ??= createSeed());
}

/** 시연용 시간 가속을 반영한 현재 시각. */
export function now() {
  return new Date(Date.now() + db().clockOffsetMs);
}

export function vehicleId() {
  return db().vehicleId;
}

function ownedCount() {
  return Object.values(db().tiles).filter((t) => t.level > 0).length;
}

function view(t: Tile, at = now()): TileView {
  const today = seoulDay(at);
  const owned = t.level > 0;
  const visited = t.visitDays.length > 0;
  return {
    ...t,
    owned,
    homeRoute: isHomeRoute(t, today),
    incomePerHour: incomePerHour(t, today),
    pending: pendingIncome(t, at),
    buyCost: !owned && visited ? buyCost(ownedCount()) : null,
    upgradeCost: owned ? upgradeCost(t.level) : null,
  };
}

/** 지나간 적이 있거나 특별 구간인 타일. */
export function listTiles() {
  const at = now();
  return Object.values(db().tiles).map((t) => view(t, at));
}

export function getTile(id: string) {
  const t = db().tiles[id];
  return t ? view(t) : null;
}

export function getToday() {
  const today = seoulDay(now());
  const trips = db().trips.filter((t) => seoulDay(t.endedAt) === today);
  return {
    trips: trips.length,
    km: Math.round(trips.reduce((s, t) => s + t.km, 0) * 10) / 10,
    newTiles: trips.reduce((s, t) => s + t.newTiles, 0),
    tilesPassed: new Set(trips.flatMap((t) => t.tiles.map((x) => x.id))).size,
    driveCredits: trips.reduce((s, t) => s + t.credits, 0),
  };
}

export function getHome() {
  const s = db();
  const tiles = listTiles();
  const owned = tiles.filter((t) => t.owned);
  return {
    credits: s.credits,
    pendingIncome: owned.reduce((n, t) => n + t.pending, 0),
    incomePerHour: owned.reduce((n, t) => n + t.incomePerHour, 0),
    idleCapHours: RULES.idleCapHours,
    ownedTiles: owned.length,
    buyableTiles: tiles.filter((t) => t.buyCost !== null).length,
    today: { ...getToday(), driveCap: RULES.dailyDriveCap },
    latestTrip: s.trips.at(-1) ?? null,
    safeStreak: s.safeStreak,
    now: now().toISOString(),
  };
}

export function recordTrip(input: TripInput): Trip {
  const s = db();
  const day = seoulDay(input.endedAt);
  const rejected = !validateTrip(input);
  const c = tripCredits(input);

  const before = getToday().driveCredits;
  const credits = rejected ? 0 : Math.max(0, Math.min(c.total, RULES.dailyDriveCap - before));
  const capReached = !rejected && before + c.total > RULES.dailyDriveCap;

  let newTiles = 0;
  if (!rejected) {
    for (const seg of input.tiles) {
      const t = (s.tiles[seg.id] ??= { id: seg.id, visitDays: [], level: 0, collectedAt: null, special: null });
      if (t.visitDays.length === 0) newTiles++;
      if (!t.visitDays.includes(day)) t.visitDays = [...t.visitDays, day].sort();
    }
    s.credits += credits;
    s.recentScores = [...s.recentScores, input.safetyScore].slice(-10);
    s.safeStreak = input.safetyScore >= RULES.safeStreakScore ? s.safeStreak + 1 : 0;
    s.bestSafeStreak = Math.max(s.bestSafeStreak, s.safeStreak);
  }

  const trip: Trip = {
    id: `t_${Date.now().toString(36)}`,
    endedAt: input.endedAt,
    km: Math.round(input.km * 10) / 10,
    safetyScore: Math.round(input.safetyScore),
    hardBrakes: input.hardBrakes,
    warnings: input.warnings,
    laneChanges: input.laneChanges,
    signaledLaneChanges: input.signaledLaneChanges,
    tiles: input.tiles,
    newTiles,
    credits,
    breakdown: { clean: c.clean, assistBonus: c.assistBonus, mannerBonus: c.mannerBonus, excluded: c.excluded },
    capReached,
    rejected,
  };
  s.trips.push(trip);
  return trip;
}

export class GameError extends Error {}

export function buyTile(id: string) {
  const s = db();
  const t = s.tiles[id];
  const v = t && view(t);
  if (!t || !v || v.buyCost === null) throw new GameError("지나간 적 있는 빈 땅만 살 수 있어요");
  if (s.credits < v.buyCost) throw new GameError("크레딧이 부족해요");
  s.credits -= v.buyCost;
  t.level = 1;
  t.collectedAt = now().toISOString();
  return view(t);
}

export function upgradeTile(id: string) {
  const s = db();
  const t = s.tiles[id];
  const v = t && view(t);
  if (!t || !v || v.upgradeCost === null) throw new GameError("더 올릴 수 없는 땅이에요");
  if (s.credits < v.upgradeCost) throw new GameError("크레딧이 부족해요");
  // 올리기 전까지 쌓인 수익은 먼저 받는다(레벨이 바뀌어 계산이 꼬이지 않게).
  s.credits += v.pending - v.upgradeCost;
  t.level += 1;
  t.collectedAt = now().toISOString();
  return view(t);
}

/** 모든 땅의 방치 수익을 한 번에 받는다. */
export function collectAll() {
  const s = db();
  const at = now();
  let total = 0;
  for (const t of Object.values(s.tiles)) {
    if (t.level <= 0) continue;
    total += pendingIncome(t, at);
    t.collectedAt = at.toISOString();
  }
  s.credits += total;
  return total;
}

export function getRecords(): Records {
  const s = db();
  const tiles = Object.values(s.tiles);
  return {
    safeStreak: s.safeStreak,
    bestSafeStreak: s.bestSafeStreak,
    recentScores: s.recentScores,
    ownedTiles: tiles.filter((t) => t.level > 0).length,
    specials: tiles.filter((t) => t.special).map((t) => ({ id: t.id, name: t.special!, collected: t.visitDays.length > 0 })),
  };
}

// 시연 도구
export function advanceClock(hours: number) {
  db().clockOffsetMs += hours * 3_600_000;
  return now();
}

export function resetState() {
  g.__rlState = createSeed();
}

export function demoRoute() {
  return sampleRoute(COMMUTE, 25);
}
