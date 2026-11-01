import BottomNav from "@/components/BottomNav";
import { requirePlayer } from "@/lib/session";

export default async function GameLayout({ children }: { children: React.ReactNode }) {
  await requirePlayer();
  return (
    <>
      <main className="shell">{children}</main>
      <BottomNav />
    </>
  );
}
