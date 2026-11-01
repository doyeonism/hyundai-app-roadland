import { gridDisk, latLngToCell } from "h3-js";
import { AXES, formName, stageForLevel, xpToNext } from "../rules";
import type { Bungbung, BuildingType, CosmeticItem, DexForm, Mission, Season, Tile, Trip, Wallet } from "../types";

// 더미 백엔드의 초기 상태. 실제 서버가 생기면 이 파일은 사라진다.

const CENTER = { lat: 37.5446, lng: 127.0559 }; // 임의의 서울 지점
export const H3_RES = 9;

export const BUILDING_TYPES: BuildingType[] = [
  { id: "house", name: "집", emoji: "🏠", coinCost: 50, coinsPerHour: 4 },
  { id: "flower", name: "꽃밭", emoji: "🌷", coinCost: 80, coinsPerHour: 6 },
  { id: "playground", name: "놀이터", emoji: "🛝", coinCost: 120, coinsPerHour: 9 },
  { id: "cafe", name: "카페", emoji: "☕", coinCost: 200, coinsPerHour: 14 },
  { id: "station", name: "충전소", emoji: "🔌", coinCost: 300, coinsPerHour: 20 },
];

const RIVALS = ["달리는토끼", "출근요정", "느긋한곰"];

function hash(s: string) {
  let h = 0;
  for (const c of s) h = (h * 31 + c.charCodeAt(0)) >>> 0;
  return h;
}

function hoursAgo(h: number) {
  return new Date(Date.now() - h * 3600_000).toISOString();
}

function seedTiles(): Record<string, Tile> {
  const origin = latLngToCell(CENTER.lat, CENTER.lng, H3_RES);
  const cells = gridDisk(origin, 6);
  const tiles: Record<string, Tile> = {};
  cells.forEach((h3, i) => {
    const r = hash(h3) % 100;
    const near = i < 37; // 대략 반경 3칸 안쪽
    let owner: Tile["owner"] = { kind: "none" };
    if (near && r < 55) owner = { kind: "me" };
    else if (r < 30) owner = { kind: "rival", name: RIVALS[r % RIVALS.length] };
    else if (r < 45) owner = { kind: "npc", name: "NPC 붕붕이" };
    tiles[h3] = {
      h3,
      owner,
      affection: owner.kind === "none" ? 0 : 10 + (r % 60),
      protectedHome: false,
      building: null,
      lastVisit: owner.kind === "me" ? hoursAgo(r % 72) : null,
      partner: r === 77 || r === 3 ? "OO 휴게소" : null,
    };
  });
  tiles[origin] = { ...tiles[origin], owner: { kind: "me" }, protectedHome: true, affection: 90, building: { typeId: "house", builtAt: hoursAgo(30) } };
  const work = cells[20];
  tiles[work] = { ...tiles[work], owner: { kind: "me" }, protectedHome: true, affection: 80, building: { typeId: "flower", builtAt: hoursAgo(12) } };
  return tiles;
}

function seedDex(): DexForm[] {
  const forms: DexForm[] = [];
  for (const main of AXES) {
    for (const sub of AXES) {
      if (sub.id === main.id) continue;
      for (const stage of [2, 3, 4] as const) {
        forms.push({
          id: `${main.id}-${sub.id}-${stage}`,
          main: main.id,
          sub: sub.id,
          stage,
          name: `${formName(main.id, stage)} · ${sub.label} 장식`,
          unlocked: false,
        });
      }
    }
  }
  return forms;
}

const level = 9;

export function createSeed() {
  const bungbung: Bungbung = {
    vehicleId: "veh_demo",
    name: "붕붕이",
    level,
    xp: 70,
    xpToNext: xpToNext(level),
    stage: stageForLevel(level),
    axes: { smooth: 72, explore: 41, eco: 55, safe: 63, steady: 80, together: 22 },
    fullness: 64,
    mood: 78,
    health: 70,
    equipped: { color: "#7aa7ff", hat: null, deco: null },
  };

  const wallet: Wallet = { food: 18, coins: 140, gems: 0, buildGauge: 100 };

  const missions: Mission[] = [
    { id: "m_brake0", text: "급제동 0회로 출근하기", progress: 1, goal: 1, reward: "먹이 5" },
    { id: "m_newtown", text: "새 동네 3곳 지나가기", progress: 1, goal: 3, reward: "코인 30" },
    { id: "m_passenger", text: "동승자와 드라이브 1번", progress: 0, goal: 1, reward: "꽃 장식" },
  ];

  const trips: Trip[] = [
    {
      id: "t_1031_eve",
      endedAt: hoursAgo(2),
      from: "회사",
      to: "집",
      km: 21.4,
      hardBrakes: 0,
      foodEarned: 12,
      newTiles: 3,
      passengers: 0,
      gaugeGained: 35,
      foodCapReached: false,
      completedMissionIds: ["m_brake0"],
      line: "새 땅 3칸! 급정거 한 번도 없었어!",
    },
    {
      id: "t_1031_morn",
      endedAt: hoursAgo(11),
      from: "집",
      to: "회사",
      km: 20.8,
      hardBrakes: 2,
      foodEarned: 8,
      newTiles: 0,
      passengers: 0,
      gaugeGained: 20,
      foodCapReached: false,
      completedMissionIds: [],
      line: "오늘은 같은 길이었네. 애정도가 올랐어!",
    },
  ];

  const items: CosmeticItem[] = [
    { id: "c_blue", slot: "color", name: "하늘", value: "#7aa7ff", owned: true },
    { id: "c_mint", slot: "color", name: "민트", value: "#5cc9a7", owned: true },
    { id: "c_pink", slot: "color", name: "벚꽃", value: "#f29bb5", owned: false },
    { id: "h_cap", slot: "hat", name: "캡모자", value: "🧢", owned: true },
    { id: "h_crown", slot: "hat", name: "왕관", value: "👑", owned: false },
    { id: "d_flower", slot: "deco", name: "꽃 장식", value: "🌸", owned: true },
    { id: "d_star", slot: "deco", name: "별 스티커", value: "⭐", owned: true },
  ];

  const seasons: Season[] = [
    {
      id: "2027-s1",
      name: "봄 꽃길",
      startsAt: "2027-03-01",
      endsAt: "2027-04-26",
      theme: "개화 지역 타일에 꽃잎 아이템이 뜨고, 꽃 구경 명소 타일은 점령 점수 2배.",
      weeklyMissions: [
        { id: "s_smooth30", text: "부드럽게 30km", progress: 18, goal: 30, reward: "패스 경험치 100" },
        { id: "s_new3", text: "새 동네 3곳", progress: 1, goal: 3, reward: "패스 경험치 80" },
        { id: "s_pass1", text: "동승자와 드라이브 1번", progress: 0, goal: 1, reward: "패스 경험치 80" },
      ],
      pass: [
        { tier: 1, free: "꽃잎 스티커", paid: "보석 30" },
        { tier: 5, free: "꽃 장식", paid: "벚꽃 색상" },
        { tier: 10, free: "코인 300", paid: "벚꽃 붕붕이 스킨" },
      ],
      passTier: 3,
    },
  ];

  return { bungbung, wallet, missions, trips, tiles: seedTiles(), dex: seedDex(), items, seasons, currentSeasonId: "2027-s1", coinsCollectedAt: hoursAgo(5) };
}

export type DummyState = ReturnType<typeof createSeed>;
