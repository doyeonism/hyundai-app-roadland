import "server-only";
import { latLngToCell } from "h3-js";
import { createSeed, BUILDING_TYPES, H3_RES, type DummyState } from "./dummy/seed";
import { foodToMax, formName, gainXp, growth, MAX_LEVEL, stageLabel, topAxes, XP_PER_FOOD } from "./rules";
import type { Axis, CosmeticItem, RankRow, Trip } from "./types";

// 더미 백엔드. 화면은 이 파일의 함수만 부른다.
// 실제 서버가 생기면 함수 안을 fetch 호출로 바꾸고 시그니처는 그대로 둔다.
// 상태는 서버 메모리에 있어 dev 서버를 재시작하면 초기화된다.

const g = globalThis as unknown as { __bbState?: DummyState };
function db(): DummyState {
  return (g.__bbState ??= createSeed());
}

export const SHARE_CODE = "family-demo";
const COIN_CAP_HOURS = 12;

export async function getBungbung() {
  return db().bungbung;
}

export async function getWallet() {
  return db().wallet;
}

export async function getMissions() {
  return db().missions;
}

export async function listTrips() {
  return [...db().trips].sort((a, b) => b.endedAt.localeCompare(a.endedAt));
}

export async function getTrip(id: string) {
  return db().trips.find((t) => t.id === id) ?? null;
}

export async function getToday() {
  const since = Date.now() - 24 * 3600_000;
  const trips = (await listTrips()).filter((t) => Date.parse(t.endedAt) >= since);
  return {
    trips,
    km: trips.reduce((s, t) => s + t.km, 0),
    food: trips.reduce((s, t) => s + t.foodEarned, 0),
    newTiles: trips.reduce((s, t) => s + t.newTiles, 0),
    hardBrakes: trips.reduce((s, t) => s + t.hardBrakes, 0),
  };
}

export async function feed(amount: number) {
  const s = db();
  const b = s.bungbung;
  const n = Math.max(0, Math.min(Math.floor(amount), s.wallet.food, foodToMax(b)));
  if (n === 0) return { evolved: false, maxed: b.level >= MAX_LEVEL };
  s.wallet.food -= n;
  b.fullness = Math.min(100, b.fullness + n * 2);
  const passed = gainXp(b, n * XP_PER_FOOD);
  if (passed.length > 0) {
    // 진화하는 순간의 운전 습관으로 모습을 정하고, 넘은 단계의 도감을 모두 연다.
    const [main, sub] = topAxes(b.axes);
    b.form = { main, sub };
    for (const stage of passed) {
      const form = s.dex.find((f) => f.id === `${main}-${sub}-${stage}`);
      if (form) form.unlocked = true;
    }
  }
  return { evolved: passed.length > 0, maxed: b.level >= MAX_LEVEL };
}

export async function getItems(): Promise<CosmeticItem[]> {
  return db().items;
}

export async function equip(slot: CosmeticItem["slot"], itemId: string | null) {
  const s = db();
  if (itemId === null) {
    if (slot !== "color") s.bungbung.equipped[slot] = null;
    return;
  }
  const item = s.items.find((i) => i.id === itemId && i.slot === slot && i.owned);
  if (!item) throw new Error("가지고 있지 않은 아이템");
  s.bungbung.equipped[slot] = item.value;
}

export async function getTiles() {
  return Object.values(db().tiles);
}

export async function getTile(h3: string) {
  return db().tiles[h3] ?? null;
}

export async function getBuildingTypes() {
  return BUILDING_TYPES;
}

export async function build(h3: string, typeId: string) {
  const s = db();
  const tile = s.tiles[h3];
  const type = BUILDING_TYPES.find((t) => t.id === typeId);
  if (!tile || !type) throw new Error("잘못된 요청");
  if (tile.owner.kind !== "me") throw new Error("내 타일에만 지을 수 있어요");
  if (tile.building) throw new Error("이미 건물이 있어요");
  if (s.wallet.buildGauge < 100) throw new Error("건물 게이지가 아직 다 차지 않았어요");
  if (s.wallet.coins < type.coinCost) throw new Error("코인이 부족해요");
  s.wallet.buildGauge = 0;
  s.wallet.coins -= type.coinCost;
  tile.building = { typeId, builtAt: new Date().toISOString() };
}

export async function pendingCoins() {
  const s = db();
  const hours = Math.min(COIN_CAP_HOURS, (Date.now() - Date.parse(s.coinsCollectedAt)) / 3600_000);
  const perHour = Object.values(s.tiles).reduce((sum, t) => {
    if (!t.building || t.owner.kind !== "me") return sum;
    return sum + (BUILDING_TYPES.find((b) => b.id === t.building!.typeId)?.coinsPerHour ?? 0);
  }, 0);
  return { coins: Math.floor(perHour * hours), perHour };
}

export async function collectCoins() {
  const { coins } = await pendingCoins();
  const s = db();
  s.wallet.coins += coins;
  s.coinsCollectedAt = new Date().toISOString();
  return coins;
}

export async function getDex() {
  return db().dex;
}

export async function getForm(id: string) {
  return db().dex.find((f) => f.id === id) ?? null;
}

export async function getRanking(kind: "tiles" | "smooth"): Promise<RankRow[]> {
  const s = db();
  const mine = Object.values(s.tiles).filter((t) => t.owner.kind === "me").length;
  const rows =
    kind === "tiles"
      ? [
          { name: "달리는토끼", value: 61 },
          { name: "출근요정", value: 44 },
          { name: s.bungbung.name, value: mine, me: true },
          { name: "느긋한곰", value: 19 },
        ]
      : [
          { name: "느긋한곰", value: 91 },
          { name: s.bungbung.name, value: s.bungbung.axes.smooth, me: true },
          { name: "출근요정", value: 66 },
          { name: "달리는토끼", value: 48 },
        ];
  return rows
    .sort((a, b) => b.value - a.value)
    .map((r, i) => ({ rank: i + 1, name: r.name, value: r.value, me: "me" in r && !!r.me }));
}

export async function getCurrentSeasonId() {
  return db().currentSeasonId;
}

export async function getSeason(id: string) {
  return db().seasons.find((x) => x.id === id) ?? null;
}

/** 동승자 링크. 붕붕이 보기와 꾸미기만 허용한다. */
export async function resolveShareCode(code: string) {
  return code === SHARE_CODE ? { vehicleId: db().bungbung.vehicleId } : null;
}

export async function linkVehicle(code: string) {
  // 실제로는 차 화면에 뜬 6자리 코드로 계정과 차량을 묶는다.
  return /^\d{6}$/.test(code) ? { vehicleId: db().bungbung.vehicleId } : null;
}

// ---- 차량 앱이 부르는 함수 ----

const DAILY_FOOD_CAP = 40;

export interface TripInput {
  startedAt: string;
  endedAt: string;
  km: number;
  hardBrakes: number;
  passengers: number;
  /** [위도, 경도]. 실제 서버에서는 차 안에서 타일 ID로 바꿔 보내고 원본 좌표는 받지 않는다. */
  points: [number, number][];
}

function clamp(v: number) {
  return Math.max(0, Math.min(100, Math.round(v)));
}

/** 주차(P)로 주행이 끝나면 차량 앱이 보내는 요약을 정산한다. */
export async function recordTrip(input: TripInput): Promise<Trip> {
  const s = db();
  const b = s.bungbung;
  const km = Math.max(0, Math.round(input.km * 10) / 10);
  const hardBrakes = Math.max(0, Math.floor(input.hardBrakes));

  const todayFood = (await getToday()).food;
  const rawFood = Math.max(0, Math.floor(km) - hardBrakes);
  const foodEarned = Math.max(0, Math.min(rawFood, DAILY_FOOD_CAP - todayFood));
  const foodCapReached = todayFood + rawFood >= DAILY_FOOD_CAP;

  let newTiles = 0;
  const smoothBonus = hardBrakes === 0 ? 2 : 1;
  for (const h3 of new Set(input.points.map(([lat, lng]) => latLngToCell(lat, lng, H3_RES)))) {
    const t = (s.tiles[h3] ??= { h3, owner: { kind: "none" }, affection: 0, protectedHome: false, building: null, lastVisit: null, partner: null });
    if (t.owner.kind === "me") {
      t.affection = clamp(t.affection + 5 * smoothBonus);
    } else if (t.owner.kind === "none") {
      t.owner = { kind: "me" };
      t.affection = 10 * smoothBonus;
      newTiles++;
    } else if (!t.protectedHome) {
      t.affection -= 10 * smoothBonus;
      if (t.affection <= 0) {
        t.owner = { kind: "me" };
        t.affection = 10;
        newTiles++;
      }
    }
    t.lastVisit = input.endedAt;
  }

  const gaugeGained = Math.min(100 - s.wallet.buildGauge, foodEarned * 3 + (hardBrakes === 0 ? 10 : 0));
  s.wallet.food += foodEarned;
  s.wallet.buildGauge += gaugeGained;

  const nudge: Partial<Record<Axis, number>> = {
    smooth: hardBrakes === 0 ? 2 : -3 * hardBrakes,
    explore: newTiles,
    together: input.passengers > 0 ? 3 : 0,
    safe: hardBrakes === 0 ? 1 : -1,
  };
  for (const [axis, d] of Object.entries(nudge) as [Axis, number][]) b.axes[axis] = clamp(b.axes[axis] + d);
  b.mood = clamp(b.mood + (hardBrakes === 0 ? 5 : -5 * hardBrakes));

  const completed: string[] = [];
  for (const m of s.missions) {
    if (m.progress >= m.goal) continue;
    if (m.id === "m_brake0" && hardBrakes === 0 && km >= 1) m.progress = m.goal;
    if (m.id === "m_newtown") m.progress = Math.min(m.goal, m.progress + newTiles);
    if (m.id === "m_passenger" && input.passengers > 0) m.progress = m.goal;
    if (m.progress >= m.goal) completed.push(m.id);
  }

  const line =
    newTiles > 0 && hardBrakes === 0
      ? `새 땅 ${newTiles}칸! 급정거 한 번도 없었어!`
      : newTiles > 0
        ? `새 땅 ${newTiles}칸! 다음엔 더 부드럽게 가 보자.`
        : hardBrakes === 0
          ? "부드러운 주행이었어. 애정도가 올랐어!"
          : `급정거 ${hardBrakes}번… 조금 놀랐어.`;

  const trip: Trip = {
    id: `t_${Date.now().toString(36)}`,
    endedAt: input.endedAt,
    // 주소 변환 전까지는 도착 시각으로 주행을 구분한다.
    from: "주행",
    to: `${new Date(input.endedAt).toLocaleTimeString("ko-KR", { hour: "numeric", minute: "2-digit", timeZone: "Asia/Seoul" })} 도착`,
    km,
    hardBrakes,
    foodEarned,
    newTiles,
    passengers: input.passengers,
    gaugeGained,
    foodCapReached,
    completedMissionIds: completed,
    line,
  };
  s.trips.push(trip);
  return trip;
}

/** 정차 중 미니게임 결과. 목욕은 기분, 달리기는 코인. */
export async function recordMinigame(game: "bath" | "run", score: number) {
  const s = db();
  const n = Math.max(0, Math.min(Math.floor(score), 200));
  if (game === "bath") {
    const mood = Math.min(30, n * 5);
    s.bungbung.mood = clamp(s.bungbung.mood + mood);
    return { mood, coins: 0 };
  }
  const coins = Math.floor(n / 2);
  s.wallet.coins += coins;
  return { mood: 0, coins };
}

/** 차량 앱 첫 화면에 필요한 것을 한 번에. */
export async function getCarHome() {
  const [b, wallet, missions, today] = await Promise.all([getBungbung(), getWallet(), getMissions(), getToday()]);
  // 차량 앱은 단계 수를 모르고, 여기서 계산한 이름과 크기를 그대로 그린다.
  const car = { mainAxis: b.form?.main ?? null, formName: formName(b.form), stageLabel: stageLabel(b.stage), growth: growth(b.stage), maxed: b.level >= MAX_LEVEL };
  return { bungbung: { ...b, ...car }, wallet, missions, latestTrip: today.trips[0] ?? null, today: { km: today.km, newTiles: today.newTiles, food: today.food } };
}
