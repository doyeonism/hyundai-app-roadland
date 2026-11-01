import { equipAction } from "@/app/actions";
import type { Bungbung, CosmeticItem } from "@/lib/types";

const SLOTS: { slot: CosmeticItem["slot"]; label: string }[] = [
  { slot: "color", label: "색상" },
  { slot: "hat", label: "모자" },
  { slot: "deco", label: "장식" },
];

/** 본인 화면과 동승자 링크가 같이 쓴다. shareCode가 있으면 동승자 모드. */
export default function CustomizePanel({ b, items, shareCode }: { b: Bungbung; items: CosmeticItem[]; shareCode?: string }) {
  return (
    <>
      {SLOTS.map(({ slot, label }) => (
        <form key={slot} action={equipAction} className="card">
          <h2>{label}</h2>
          <input type="hidden" name="slot" value={slot} />
          {shareCode && <input type="hidden" name="share" value={shareCode} />}
          <div className="choice">
            {slot !== "color" && (
              <button name="itemId" value="" aria-pressed={b.equipped[slot] === null}>
                <span style={{ fontSize: "1.3rem" }}>∅</span>
                없음
              </button>
            )}
            {items
              .filter((i) => i.slot === slot)
              .map((i) => (
                <button key={i.id} name="itemId" value={i.id} disabled={!i.owned} aria-pressed={b.equipped[slot] === i.value}>
                  {slot === "color" ? (
                    <span style={{ width: 22, height: 22, borderRadius: 6, background: i.value, display: "inline-block" }} />
                  ) : (
                    <span style={{ fontSize: "1.3rem" }}>{i.value}</span>
                  )}
                  {i.name}
                  {!i.owned && <span className="muted">🔒</span>}
                </button>
              ))}
          </div>
        </form>
      ))}
    </>
  );
}
