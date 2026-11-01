import { NextResponse } from "next/server";
import { revalidatePath } from "next/cache";
import { recordTrip, type TripInput } from "@/lib/api";
import { checkVehicle } from "../_auth";

export async function POST(req: Request) {
  const denied = await checkVehicle(req);
  if (denied) return denied;
  const body = (await req.json()) as Partial<TripInput>;
  if (typeof body.km !== "number" || !Array.isArray(body.points)) {
    return NextResponse.json({ error: "bad_request" }, { status: 400 });
  }
  const now = new Date().toISOString();
  const trip = await recordTrip({
    startedAt: body.startedAt ?? now,
    endedAt: body.endedAt ?? now,
    km: body.km,
    hardBrakes: body.hardBrakes ?? 0,
    passengers: body.passengers ?? 0,
    points: body.points,
  });
  revalidatePath("/", "layout");
  return NextResponse.json(trip, { status: 201 });
}
