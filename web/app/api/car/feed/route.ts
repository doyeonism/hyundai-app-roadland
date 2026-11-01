import { NextResponse } from "next/server";
import { revalidatePath } from "next/cache";
import { feed, getCarHome } from "@/lib/api";
import { checkVehicle } from "../_auth";

export async function POST(req: Request) {
  const denied = await checkVehicle(req);
  if (denied) return denied;
  const { amount } = (await req.json()) as { amount?: number };
  const { evolved } = await feed(Number(amount ?? 0));
  revalidatePath("/", "layout");
  return NextResponse.json({ evolved, home: await getCarHome() });
}
