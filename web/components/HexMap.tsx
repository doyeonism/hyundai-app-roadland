import Link from "next/link";
import { cellToBoundary } from "h3-js";
import type { BuildingType, Tile } from "@/lib/types";

const FILL: Record<Tile["owner"]["kind"], string> = {
  me: "var(--me)",
  rival: "var(--rival)",
  npc: "var(--npc)",
  none: "var(--empty)",
};

/** H3 타일을 지도 배경 없이 SVG 육각형으로 그린다. */
export default function HexMap({ tiles, types, highlight }: { tiles: Tile[]; types: BuildingType[]; highlight?: string }) {
  const polys = tiles.map((t) => ({ t, pts: cellToBoundary(t.h3) }));
  const all = polys.flatMap((p) => p.pts);
  const lats = all.map((p) => p[0]);
  const lngs = all.map((p) => p[1]);
  const minLat = Math.min(...lats), maxLat = Math.max(...lats);
  const minLng = Math.min(...lngs), maxLng = Math.max(...lngs);
  const kx = Math.cos(((minLat + maxLat) / 2) * (Math.PI / 180));
  const W = 400;
  const scale = W / ((maxLng - minLng) * kx);
  const H = (maxLat - minLat) * scale;
  const proj = ([lat, lng]: number[]) => [(lng - minLng) * kx * scale, (maxLat - lat) * scale] as const;

  return (
    <svg className="hexmap" viewBox={`-4 -4 ${W + 8} ${H + 8}`} role="img" aria-label="내 주변 타일 지도">
      {polys.map(({ t, pts }) => {
        const xy = pts.map(proj);
        const cx = xy.reduce((s, p) => s + p[0], 0) / xy.length;
        const cy = xy.reduce((s, p) => s + p[1], 0) / xy.length;
        const b = t.building && types.find((x) => x.id === t.building!.typeId);
        return (
          <Link key={t.h3} href={`/map/${t.h3}`} aria-label={`타일 ${t.h3}`}>
            <polygon
              points={xy.map((p) => p.join(",")).join(" ")}
              fill={FILL[t.owner.kind]}
              opacity={t.owner.kind === "me" ? 0.45 + Math.min(t.affection, 100) / 200 : 0.9}
              strokeWidth={t.h3 === highlight ? 3 : undefined}
              style={t.h3 === highlight ? { stroke: "var(--text)" } : undefined}
            />
            {(b || t.protectedHome || t.partner) && (
              <text x={cx} y={cy + 5} fontSize="14" textAnchor="middle" pointerEvents="none">
                {b ? b.emoji : t.partner ? "⭐" : "🏡"}
              </text>
            )}
          </Link>
        );
      })}
    </svg>
  );
}

export function MapLegend() {
  return (
    <div className="legend">
      <span><i style={{ background: "var(--me)" }} />내 땅</span>
      <span><i style={{ background: "var(--rival)" }} />라이벌</span>
      <span><i style={{ background: "var(--npc)" }} />NPC</span>
      <span><i style={{ background: "var(--empty)" }} />빈 땅</span>
      <span>⭐ 제휴 거점</span>
    </div>
  );
}
