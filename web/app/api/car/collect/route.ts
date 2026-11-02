import { collectAll } from "@/lib/api";
import { handle } from "../_auth";

export async function POST(req: Request) {
  return handle(req, () => ({ collected: collectAll() }));
}
