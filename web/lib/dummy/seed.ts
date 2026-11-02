import { tileForLatLng } from "../hex";
import { daysAgo, seoulDay } from "../rules";
import type { Tile, Trip } from "../types";

/** 시연용 출퇴근 경로(성수동 집 → 서울숲 → 성수대교 → 강남역 회사). 위도, 경도. */
export const COMMUTE: [number, number][] = [
  [37.5446, 127.0559],
  [37.5447, 127.0466],
  [37.5444, 127.0374],
  [37.5378, 127.035],
  [37.5303, 127.0336],
  [37.5226, 127.0318],
  [37.5135, 127.0297],
  [37.5049, 127.0272],
  [37.4979, 127.0276],
];

/** 경로를 약 30m 간격 점으로 펼친다. */
export function sampleRoute(route: [number, number][], stepM = 30): [number, number][] {
  const out: [number, number][] = [];
  for (let i = 0; i < route.length - 1; i++) {
    const [a, b] = [route[i], route[i + 1]];
    const dLat = (b[0] - a[0]) * 111_320;
    const dLng = (b[1] - a[1]) * 111_320 * Math.cos((a[0] * Math.PI) / 180);
    const n = Math.max(1, Math.ceil(Math.hypot(dLat, dLng) / stepM));
    for (let k = 0; k < n; k++) out.push([a[0] + ((b[0] - a[0]) * k) / n, a[1] + ((b[1] - a[1]) * k) / n]);
  }
  out.push(route[route.length - 1]);
  return out;
}

export function routeTiles(route: [number, number][]) {
  return [...new Set(sampleRoute(route).map(([lat, lng]) => tileForLatLng(lat, lng)))];
}

const SPECIALS: { name: string; at: [number, number] }[] = [
  { name: "서울숲", at: [37.5444, 127.0374] },
  { name: "성수대교", at: [37.5378, 127.035] },
  { name: "한강 뚝섬", at: [37.5296, 127.0705] },
  { name: "강남역", at: [37.4979, 127.0276] },
];

export function createSeed(now = new Date()) {
  const today = seoulDay(now);
  const tiles: Record<string, Tile> = {};
  const touch = (id: string, days: string[]) => {
    const t = (tiles[id] ??= { id, visitDays: [], level: 0, collectedAt: null, special: null });
    t.visitDays = [...new Set([...t.visitDays, ...days])].sort();
    return t;
  };

  // 집 근처 앞쪽 절반은 지난 며칠 출근길로 지나간 적이 있다(홈 루트). 그중 몇 칸은 이미 내 땅.
  const commute = routeTiles(COMMUTE);
  const pastDays = [daysAgo(today, 1), daysAgo(today, 2), daysAgo(today, 4)];
  const known = commute.slice(0, Math.ceil(commute.length / 2));
  known.forEach((id, i) => {
    const t = touch(id, pastDays);
    if (i % 3 === 0 && i < 12) {
      t.level = i < 4 ? 2 : 1;
      t.collectedAt = new Date(now.getTime() - 5 * 3_600_000).toISOString();
    }
  });
  // 주말에 한 번 다녀온 한강 쪽 길.
  routeTiles([
    [37.5446, 127.0559],
    [37.5371, 127.0632],
    [37.5296, 127.0705],
  ]).forEach((id) => touch(id, [daysAgo(today, 5)]));

  for (const s of SPECIALS) {
    const id = tileForLatLng(...s.at);
    (tiles[id] ??= { id, visitDays: [], level: 0, collectedAt: null, special: null }).special = s.name;
  }

  const trips: Trip[] = [];
  return {
    vehicleId: "veh_demo",
    credits: 120,
    tiles,
    trips,
    safeStreak: 4,
    bestSafeStreak: 5,
    recentScores: [88, 92, 95, 97, 100],
    clockOffsetMs: 0,
  };
}

export type DummyState = ReturnType<typeof createSeed>;
