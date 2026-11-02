import { resetState } from "@/lib/api";
import { handle } from "../../_auth";

export async function POST(req: Request) {
  return handle(req, () => (resetState(), { ok: true }));
}
