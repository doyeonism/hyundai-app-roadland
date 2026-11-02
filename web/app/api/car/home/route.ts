import { getHome } from "@/lib/api";
import { handle } from "../_auth";

export async function GET(req: Request) {
  return handle(req, getHome);
}
