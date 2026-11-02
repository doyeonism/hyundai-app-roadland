import { NextResponse } from "next/server";
import { recordTrip } from "@/lib/api";
import type { TileSegment, TripInput } from "@/lib/types";
import { checkVehicle } from "../_auth";

const num = (v: unknown) => (typeof v === "number" && Number.isFinite(v) ? v : 0);

export async function POST(req: Request) {
  const denied = checkVehicle(req);
  if (denied) return denied;
  const body = (await req.json()) as Partial<TripInput>;
  if (typeof body.km !== "number" || !Array.isArray(body.tiles) || !body.startedAt || !body.endedAt) {
    return NextResponse.json({ error: "bad_request" }, { status: 400 });
  }
  const tiles: TileSegment[] = body.tiles
    .filter((t) => typeof t?.id === "string" && /^-?\d+:-?\d+$/.test(t.id))
    .map((t) => ({ id: t.id, hardBrake: !!t.hardBrake, warning: !!t.warning, assisted: !!t.assisted }));
  const trip = recordTrip({
    startedAt: body.startedAt,
    endedAt: body.endedAt,
    km: body.km,
    safetyScore: num(body.safetyScore),
    hardBrakes: num(body.hardBrakes),
    warnings: num(body.warnings),
    laneChanges: num(body.laneChanges),
    signaledLaneChanges: num(body.signaledLaneChanges),
    tiles,
  });
  return NextResponse.json(trip, { status: 201 });
}
