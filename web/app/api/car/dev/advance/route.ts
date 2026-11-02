import { advanceClock } from "@/lib/api";
import { handle } from "../../_auth";

// 시연용 시간 가속: 서버 시계를 앞으로 돌려 방치 수익이 쌓인 상태를 만든다.
export async function POST(req: Request) {
  const { hours } = (await req.json()) as { hours?: number };
  return handle(req, () => ({ now: advanceClock(Math.max(0, Math.min(Number(hours) || 0, 48))) }));
}
