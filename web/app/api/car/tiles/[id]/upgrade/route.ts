import { upgradeTile } from "@/lib/api";
import { handle } from "../../../_auth";

export async function POST(req: Request, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return handle(req, () => upgradeTile(decodeURIComponent(id)));
}
