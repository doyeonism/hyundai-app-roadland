import "server-only";
import { createSeed, BUILDING_TYPES, type DummyState } from "./dummy/seed";
import { stageForLevel, topAxes, xpToNext, XP_PER_FOOD } from "./rules";
import type { CosmeticItem, RankRow } from "./types";

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
  const n = Math.max(0, Math.min(amount, s.wallet.food));
  if (n === 0) return { evolved: false };
  s.wallet.food -= n;
  const b = s.bungbung;
  const before = b.stage;
  b.xp += n * XP_PER_FOOD;
  b.fullness = Math.min(100, b.fullness + n * 2);
  while (b.xp >= b.xpToNext) {
    b.xp -= b.xpToNext;
    b.level += 1;
    b.xpToNext = xpToNext(b.level);
  }
  b.stage = stageForLevel(b.level);
  const evolved = b.stage !== before;
  if (evolved) {
    const [main, sub] = topAxes(b.axes);
    const form = s.dex.find((f) => f.id === `${main}-${sub}-${b.stage}`);
    if (form) form.unlocked = true;
  }
  return { evolved };
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
