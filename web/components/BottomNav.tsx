"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const TABS = [
  { href: "/", label: "오늘", ico: "📋" },
  { href: "/bungbung", label: "붕붕이", ico: "🚗" },
  { href: "/map", label: "지도", ico: "⬢" },
  { href: "/dex", label: "도감", ico: "📖" },
  { href: "/rank", label: "순위", ico: "🏆" },
];

export default function BottomNav() {
  const path = usePathname();
  return (
    <nav className="nav" aria-label="주 메뉴">
      <ul>
        {TABS.map((t) => {
          const active = t.href === "/" ? path === "/" : path.startsWith(t.href);
          return (
            <li key={t.href}>
              <Link href={t.href} aria-current={active ? "page" : undefined}>
                <span className="ico" aria-hidden>
                  {t.ico}
                </span>
                {t.label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
