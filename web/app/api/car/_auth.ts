import { NextResponse } from "next/server";
import { GameError, vehicleId } from "@/lib/api";

// 더미 차량 인증: 헤더의 차량 ID만 확인한다. 실제로는 기기 토큰으로 바꾼다.
export function checkVehicle(req: Request) {
  const id = req.headers.get("x-vehicle-id");
  return id && id === vehicleId() ? null : NextResponse.json({ error: "unknown_vehicle" }, { status: 401 });
}

/** 인증 뒤 핸들러를 실행하고, 게임 규칙 위반은 400과 안내 문구로 돌려준다. */
export async function handle(req: Request, run: () => unknown | Promise<unknown>, status = 200) {
  const denied = checkVehicle(req);
  if (denied) return denied;
  try {
    return NextResponse.json(await run(), { status });
  } catch (e) {
    if (e instanceof GameError) return NextResponse.json({ error: e.message }, { status: 400 });
    throw e;
  }
}
