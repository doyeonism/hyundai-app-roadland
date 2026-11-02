import { describe, expect, it } from "vitest";
import { hexDistance, tileForLatLng } from "./hex";

// 차량 앱 HexGridTest.kt와 같은 값. 한쪽을 바꾸면 다른 쪽도 바꿔야 한다.
export const VECTORS: [number, number, string][] = [
  [37.5446, 127.0559, "44406:20068"],
  [37.4979, 127.0276, "44408:20039"],
  [35.1796, 129.0756, "45997:18615"],
  [0, 0, "0:0"],
  [-33.8688, 151.2093, "73702:-17827"],
];

describe("hex", () => {
  it("좌표를 차량 앱과 같은 타일로 바꾼다", () => {
    for (const [lat, lng, id] of VECTORS) expect(tileForLatLng(lat, lng)).toBe(id);
  });

  it("가까운 점은 같은 타일, 먼 점은 다른 타일", () => {
    expect(tileForLatLng(37.5446, 127.0559)).toBe(tileForLatLng(37.54461, 127.05591));
    expect(hexDistance(tileForLatLng(37.5446, 127.0559), tileForLatLng(37.4979, 127.0276))).toBeGreaterThan(20);
  });
});
