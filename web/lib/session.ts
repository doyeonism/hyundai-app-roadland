import "server-only";
import { cookies } from "next/headers";
import { redirect } from "next/navigation";

// 더미 세션: 쿠키 두 개로 로그인과 차량 연결 여부만 기억한다.
const USER = "bb_user";
const VEHICLE = "bb_vehicle";

export async function getSession() {
  const c = await cookies();
  return { user: c.get(USER)?.value ?? null, vehicleId: c.get(VEHICLE)?.value ?? null };
}

/** (game) 화면 진입 조건: 로그인 + 차량 연결. */
export async function requirePlayer() {
  const s = await getSession();
  if (!s.user) redirect("/login");
  if (!s.vehicleId) redirect("/link");
  return s as { user: string; vehicleId: string };
}

export async function setUser(name: string) {
  (await cookies()).set(USER, name, { httpOnly: true, sameSite: "lax", path: "/" });
}

export async function setVehicle(id: string) {
  (await cookies()).set(VEHICLE, id, { httpOnly: true, sameSite: "lax", path: "/" });
}

export async function clearSession() {
  const c = await cookies();
  c.delete(USER);
  c.delete(VEHICLE);
}
