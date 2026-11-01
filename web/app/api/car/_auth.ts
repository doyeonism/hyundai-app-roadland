import { NextResponse } from "next/server";
import { getBungbung } from "@/lib/api";

// 더미 차량 인증: 헤더의 차량 ID만 확인한다. 실제로는 기기 토큰으로 바꾼다.
export async function checkVehicle(req: Request) {
  const id = req.headers.get("x-vehicle-id");
  if (!id || id !== (await getBungbung()).vehicleId) {
    return NextResponse.json({ error: "unknown_vehicle" }, { status: 401 });
  }
  return null;
}
