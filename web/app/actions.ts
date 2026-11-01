"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import * as api from "@/lib/api";
import { clearSession, requirePlayer, setUser, setVehicle } from "@/lib/session";
import type { CosmeticItem } from "@/lib/types";

export type ActionResult = { ok: boolean; message: string } | null;

export async function loginAction(_: ActionResult, form: FormData): Promise<ActionResult> {
  const name = String(form.get("name") ?? "").trim();
  if (!name) return { ok: false, message: "닉네임을 입력해 주세요" };
  await setUser(name);
  redirect("/link");
}

export async function linkAction(_: ActionResult, form: FormData): Promise<ActionResult> {
  const res = await api.linkVehicle(String(form.get("code") ?? "").trim());
  if (!res) return { ok: false, message: "차 화면에 보이는 6자리 숫자를 입력해 주세요" };
  await setVehicle(res.vehicleId);
  redirect("/");
}

export async function logoutAction() {
  await clearSession();
  redirect("/login");
}

export async function feedAction(_: ActionResult, form: FormData): Promise<ActionResult> {
  await requirePlayer();
  const { evolved } = await api.feed(Number(form.get("amount") ?? 0));
  revalidatePath("/", "layout");
  return { ok: true, message: evolved ? "붕붕이가 진화했어요! 도감에 새 모습이 등록됐어요." : "냠냠! 경험치가 올랐어요." };
}

export async function collectAction(): Promise<ActionResult> {
  await requirePlayer();
  const coins = await api.collectCoins();
  revalidatePath("/", "layout");
  return { ok: true, message: coins > 0 ? `코인 ${coins}개를 받았어요` : "아직 쌓인 코인이 없어요" };
}

export async function buildAction(_: ActionResult, form: FormData): Promise<ActionResult> {
  await requirePlayer();
  const h3 = String(form.get("h3"));
  try {
    await api.build(h3, String(form.get("typeId")));
  } catch (e) {
    return { ok: false, message: (e as Error).message };
  }
  revalidatePath("/", "layout");
  return { ok: true, message: "건물을 지었어요!" };
}

/** 본인과 동승자 링크 둘 다 쓴다. 동승자는 share 코드로 확인한다. */
export async function equipAction(form: FormData) {
  const code = form.get("share");
  if (code) {
    if (!(await api.resolveShareCode(String(code)))) throw new Error("만료된 링크");
  } else {
    await requirePlayer();
  }
  const slot = String(form.get("slot")) as CosmeticItem["slot"];
  const itemId = form.get("itemId") ? String(form.get("itemId")) : null;
  await api.equip(slot, itemId);
  revalidatePath("/", "layout");
}
