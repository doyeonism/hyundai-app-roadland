import Link from "next/link";
import { notFound } from "next/navigation";
import { gridDisk } from "h3-js";
import HexMap from "@/components/HexMap";
import Bar from "@/components/Bar";
import { BuildForm } from "@/components/forms";
import { getBuildingTypes, getTile, getTiles, getWallet } from "@/lib/api";
import type { Tile } from "@/lib/types";

function ownerLabel(o: Tile["owner"]) {
  switch (o.kind) {
    case "me":
      return "내 땅";
    case "rival":
      return `${o.name}의 땅`;
    case "npc":
      return o.name;
    default:
      return "빈 땅";
  }
}

export default async function TilePage({ params }: { params: Promise<{ h3: string }> }) {
  const { h3 } = await params;
  const tile = await getTile(h3);
  if (!tile) notFound();
  const [all, types, wallet] = await Promise.all([getTiles(), getBuildingTypes(), getWallet()]);
  const near = new Set(gridDisk(h3, 2));
  const building = tile.building && types.find((t) => t.id === tile.building!.typeId);

  return (
    <>
      <div className="row between">
        <h1>{ownerLabel(tile.owner)}</h1>
        <Link href="/map" className="muted">
          ‹ 지도
        </Link>
      </div>
      <section className="card">
        <HexMap tiles={all.filter((t) => near.has(t.h3))} types={types} highlight={h3} />
      </section>
      <section className="card">
        <div className="row" style={{ flexWrap: "wrap" }}>
          {tile.protectedHome && <span className="pill">🏡 우리 집 보호</span>}
          {tile.partner && <span className="pill">⭐ 제휴 거점 · {tile.partner}</span>}
        </div>
        <Bar label="애정도" value={tile.affection} />
        <p className="muted">
          {tile.owner.kind === "me"
            ? "지나갈 때마다 애정도가 올라요. 남이 이 땅을 가져가려면 점령 점수가 애정도를 넘어야 해요."
            : tile.owner.kind === "none"
              ? "지나가기만 하면 바로 내 땅이 돼요."
              : "부드럽게 지나갈수록 점령 점수가 더 올라요."}
        </p>
        {building && (
          <div className="row">
            <span style={{ fontSize: "2rem" }}>{building.emoji}</span>
            <div>
              <strong>{building.name}</strong>
              <div className="muted">시간당 {building.coinsPerHour}코인</div>
            </div>
          </div>
        )}
        <div className="muted" style={{ fontSize: "0.7rem" }}>H3 {tile.h3}</div>
      </section>
      {tile.owner.kind === "me" && !tile.building && <BuildForm h3={h3} types={types} coins={wallet.coins} gauge={wallet.buildGauge} />}
    </>
  );
}
