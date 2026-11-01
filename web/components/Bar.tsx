export default function Bar({ label, value, max = 100, color }: { label: string; value: number; max?: number; color?: string }) {
  const pct = Math.max(0, Math.min(100, (value / max) * 100));
  return (
    <div>
      <div className="row between muted">
        <span>{label}</span>
        <span>
          {value}
          {max !== 100 && ` / ${max}`}
        </span>
      </div>
      <div className="bar" role="meter" aria-label={label} aria-valuenow={value} aria-valuemin={0} aria-valuemax={max}>
        <span style={{ width: `${pct}%`, background: color }} />
      </div>
    </div>
  );
}
