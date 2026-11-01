import HexMap, { MapLegend } from "@/components/HexMap";
import Bar from "@/components/Bar";
import { getBuildingTypes, getTiles, getWallet } from "@/lib/api";

export default async function MapPage() {
  const [tiles, types, wallet] = await Promise.all([getTiles(), getBuildingTypes(), getWallet()]);
  const mine = tiles.filter((t) => t.owner.kind === "me");
  const built = mine.filter((t) => t.building).length;

  return (
    <>
      <h1>내 땅</h1>
      <section className="card">
        <div className="grid3">
          <div>
            <div className="big">{mine.length}</div>
            <div className="muted">내 타일</div>
          </div>
          <div>
            <div className="big">{built}</div>
            <div className="muted">건물</div>
          </div>
          <div>
            <div className="big">{mine.filter((t) => t.protectedHome).length}</div>
            <div className="muted">보호 타일</div>
          </div>
        </div>
        <Bar label="건물 게이지" value={wallet.buildGauge} />
      </section>
      <section className="card">
        <HexMap tiles={tiles} types={types} />
        <MapLegend />
        <p className="muted">타일을 누르면 상세와 건물 짓기가 열려요. 진한 파란색일수록 애정도가 높아요.</p>
      </section>
    </>
  );
}
