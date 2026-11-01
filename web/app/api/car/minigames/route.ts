import { NextResponse } from "next/server";
import { revalidatePath } from "next/cache";
import { recordMinigame } from "@/lib/api";
import { checkVehicle } from "../_auth";

export async function POST(req: Request) {
  const denied = await checkVehicle(req);
  if (denied) return denied;
  const { game, score } = (await req.json()) as { game?: string; score?: number };
  if (game !== "bath" && game !== "run") return NextResponse.json({ error: "bad_request" }, { status: 400 });
  const reward = await recordMinigame(game, Number(score ?? 0));
  revalidatePath("/", "layout");
  return NextResponse.json(reward);
}
