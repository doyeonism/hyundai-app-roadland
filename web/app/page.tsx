// 로드랜드 더미 서버. 화면은 차량 앱에 있고, 여기서는 차량 API만 제공한다.
const ROUTES = [
  "GET  /api/car/home",
  "POST /api/car/trips",
  "GET  /api/car/territory",
  "POST /api/car/tiles/{id}/buy",
  "POST /api/car/tiles/{id}/upgrade",
  "POST /api/car/collect",
  "GET  /api/car/records",
  "POST /api/car/dev/advance  { hours }",
  "POST /api/car/dev/reset",
  "GET  /api/car/dev/route",
];

export default function Page() {
  return (
    <main style={{ padding: 24, fontFamily: "system-ui" }}>
      <h1>로드랜드 더미 서버</h1>
      <p>모든 요청에 헤더 x-vehicle-id: veh_demo 가 필요합니다.</p>
      <pre>{ROUTES.join("\n")}</pre>
    </main>
  );
}
