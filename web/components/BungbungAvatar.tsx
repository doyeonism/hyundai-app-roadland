import { topAxes, AXIS_COLOR } from "@/lib/rules";
import type { Bungbung } from "@/lib/types";

// 기본형 임시 그래픽. 최종 아트가 오면 이 컴포넌트만 바꾼다.
const MAIN_MARK: Record<string, string> = {
  smooth: "☁️",
  explore: "⛺",
  eco: "🌱",
  safe: "🛡️",
  steady: "⏰",
  together: "🪟",
};

export default function BungbungAvatar({ b, size = 160 }: { b: Pick<Bungbung, "stage" | "axes" | "equipped">; size?: number }) {
  const [main, sub] = topAxes(b.axes);
  const scale = 0.7 + b.stage * 0.1;
  return (
    <svg viewBox="0 0 200 160" width={size} height={(size * 160) / 200} role="img" aria-label="붕붕이">
      <g transform={`translate(100 92) scale(${scale}) translate(-100 -92)`}>
        <rect x="35" y="60" width="130" height="58" rx="26" fill={b.equipped.color} />
        <rect x="62" y="34" width="76" height="40" rx="18" fill={b.equipped.color} />
        <rect x="72" y="42" width="56" height="24" rx="10" fill="#ffffff" opacity="0.85" />
        <circle cx="88" cy="54" r="4" fill="#1f2328" />
        <circle cx="112" cy="54" r="4" fill="#1f2328" />
        <path d="M92 62 Q100 68 108 62" stroke="#1f2328" strokeWidth="3" fill="none" strokeLinecap="round" />
        <circle cx="68" cy="120" r="15" fill="#2b2f36" />
        <circle cx="132" cy="120" r="15" fill="#2b2f36" />
        {b.stage > 1 && <rect x="45" y="84" width="110" height="8" rx="4" fill={AXIS_COLOR[sub]} />}
      </g>
      {b.stage > 1 && (
        <text x="160" y="40" fontSize="26" textAnchor="middle">
          {MAIN_MARK[main]}
        </text>
      )}
      {b.equipped.hat && (
        <text x="100" y={36 - b.stage * 4} fontSize="30" textAnchor="middle">
          {b.equipped.hat}
        </text>
      )}
      {b.equipped.deco && (
        <text x="42" y="70" fontSize="22" textAnchor="middle">
          {b.equipped.deco}
        </text>
      )}
    </svg>
  );
}
