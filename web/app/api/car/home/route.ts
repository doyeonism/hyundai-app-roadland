import { NextResponse } from "next/server";
import { getCarHome } from "@/lib/api";
import { checkVehicle } from "../_auth";

export async function GET(req: Request) {
  return (await checkVehicle(req)) ?? NextResponse.json(await getCarHome());
}
