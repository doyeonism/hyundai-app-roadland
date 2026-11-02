import { demoRoute } from "@/lib/api";
import { handle } from "../../_auth";

// 시연 스크립트가 에뮬레이터 GPS로 재생할 출퇴근 경로.
export async function GET(req: Request) {
  return handle(req, () => ({ points: demoRoute() }));
}
