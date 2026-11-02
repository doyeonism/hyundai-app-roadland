import { beforeEach, describe, expect, it } from "vitest";
import { advanceClock, buyTile, collectAll, getHome, getRecords, getTile, GameError, listTiles, now, recordTrip, resetState, upgradeTile } from "./api";
import { RULES } from "./rules";
import type { TripInput } from "./types";

beforeEach(() => resetState());

const drive = (ids: string[], o: Partial<TripInput> = {}) => {
  const end = now();
  return recordTrip({
    startedAt: new Date(end.getTime() - 20 * 60_000).toISOString(),
    endedAt: end.toISOString(),
    km: 10,
    safetyScore: 100,
    hardBrakes: 0,
    warnings: 0,
    laneChanges: 0,
    signaledLaneChanges: 0,
    tiles: ids.map((id) => ({ id, hardBrake: false, warning: false, assisted: false })),
    ...o,
  });
};

describe("주행 → 땅 → 방치 수익", () => {
  it("처음 지나간 타일은 살 수 있게 되고, 크레딧이 쌓인다", () => {
    const before = getHome().credits;
    const t = drive(["900:1", "900:2"]);
    expect(t.newTiles).toBe(2);
    expect(getHome().credits).toBe(before + 2 * RULES.tileCredit);
    expect(getTile("900:1")?.buyCost).not.toBeNull();
  });

  it("지나간 적 없는 땅은 살 수 없다", () => {
    expect(() => buyTile("999:999")).toThrow(GameError);
  });

  it("땅을 사고 올리면 시간이 지나 수익이 생기고, 한 번에 받는다", () => {
    drive(["900:1"]);
    buyTile("900:1");
    upgradeTile("900:1");
    expect(getTile("900:1")?.level).toBe(2);
    collectAll();
    advanceClock(3);
    const pending = getHome().pendingIncome;
    expect(pending).toBeGreaterThan(0);
    const credits = getHome().credits;
    expect(collectAll()).toBe(pending);
    expect(getHome().credits).toBe(credits + pending);
    expect(getHome().pendingIncome).toBe(0);
  });

  it("크레딧이 모자라면 살 수 없다", () => {
    drive(["900:1"]);
    const owned = listTiles().filter((t) => t.owned).length;
    // 크레딧을 다 써 버린다
    while (getHome().credits >= RULES.buyBaseCost + RULES.buyCostPerOwned * owned) {
      const free = listTiles().find((t) => t.buyCost !== null && t.buyCost <= getHome().credits);
      if (!free) break;
      buyTile(free.id);
    }
    const target = listTiles().find((t) => t.buyCost !== null)!;
    expect(() => buyTile(target.id)).toThrow("크레딧이 부족해요");
  });

  it("하루 주행 크레딧은 상한까지만", () => {
    const many = Array.from({ length: 60 }, (_, i) => `800:${i}`);
    drive(many, { km: 10 });
    const t = drive(Array.from({ length: 60 }, (_, i) => `801:${i}`), { km: 10 });
    expect(t.capReached).toBe(true);
    expect(getHome().today.driveCredits).toBe(RULES.dailyDriveCap);
  });

  it("검증에 실패한 기록은 보상도 땅도 없다", () => {
    const t = drive(["700:1"], { km: 500 });
    expect(t.rejected).toBe(true);
    expect(t.credits).toBe(0);
    expect(getTile("700:1")).toBeNull();
  });

  it("안전 점수 90 이상이 이어지면 연속 기록, 아니면 끊긴다", () => {
    const start = getRecords().safeStreak;
    drive(["1:1"], { safetyScore: 95 });
    expect(getRecords().safeStreak).toBe(start + 1);
    drive(["1:2"], { safetyScore: 60, hardBrakes: 2 });
    expect(getRecords().safeStreak).toBe(0);
  });
});
