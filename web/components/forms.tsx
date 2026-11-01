"use client";

import { useActionState, useState } from "react";
import { buildAction, collectAction, feedAction, linkAction, loginAction, type ActionResult } from "@/app/actions";
import type { BuildingType } from "@/lib/types";

function Msg({ state }: { state: ActionResult }) {
  if (!state) return null;
  return (
    <p className={state.ok ? "msg-ok" : "msg-bad"} role="status">
      {state.message}
    </p>
  );
}

export function LoginForm() {
  const [state, action, pending] = useActionState(loginAction, null);
  return (
    <form action={action} className="card">
      <label htmlFor="name">닉네임</label>
      <input id="name" name="name" className="input" placeholder="예: 출근요정" autoComplete="nickname" />
      <button className="btn" disabled={pending}>
        시작하기
      </button>
      <Msg state={state} />
    </form>
  );
}

export function LinkForm() {
  const [state, action, pending] = useActionState(linkAction, null);
  return (
    <form action={action} className="card">
      <label htmlFor="code">차 화면의 연결 코드</label>
      <input id="code" name="code" className="input" inputMode="numeric" maxLength={6} placeholder="6자리 숫자" />
      <button className="btn" disabled={pending}>
        내 차와 연결
      </button>
      <Msg state={state} />
    </form>
  );
}

export function FeedForm({ food }: { food: number }) {
  const [state, action, pending] = useActionState(feedAction, null);
  const [amount, setAmount] = useState(Math.min(5, food));
  return (
    <form action={action} className="card">
      <div className="row between">
        <h2>먹이 주기</h2>
        <span className="muted">가진 먹이 {food}</span>
      </div>
      <input type="range" name="amount" min={0} max={food} value={Math.min(amount, food)} onChange={(e) => setAmount(Number(e.target.value))} aria-label="줄 먹이 개수" />
      <button className="btn" disabled={pending || food === 0 || amount === 0}>
        먹이 {Math.min(amount, food)}개 주기
      </button>
      <Msg state={state} />
    </form>
  );
}

export function CollectButton({ coins, perHour }: { coins: number; perHour: number }) {
  const [state, action, pending] = useActionState(collectAction, null);
  return (
    <form action={action} className="card">
      <div className="row between">
        <h2>건물 수익</h2>
        <span className="muted">시간당 {perHour}코인</span>
      </div>
      <button className="btn" disabled={pending || coins === 0}>
        코인 {coins}개 받기
      </button>
      <Msg state={state} />
    </form>
  );
}

export function BuildForm({ h3, types, coins, gauge }: { h3: string; types: BuildingType[]; coins: number; gauge: number }) {
  const [state, action, pending] = useActionState(buildAction, null);
  const ready = gauge >= 100;
  return (
    <form action={action} className="card">
      <div className="row between">
        <h2>건물 짓기</h2>
        <span className="muted">게이지 {gauge}% · 코인 {coins}</span>
      </div>
      {!ready && <p className="muted">운전해서 건물 게이지를 100%까지 채우면 지을 수 있어요.</p>}
      <input type="hidden" name="h3" value={h3} />
      <div className="choice">
        {types.map((t) => (
          <button key={t.id} name="typeId" value={t.id} disabled={pending || !ready || coins < t.coinCost}>
            <span style={{ fontSize: "1.4rem" }}>{t.emoji}</span>
            {t.name}
            <span className="muted">{t.coinCost}코인</span>
          </button>
        ))}
      </div>
      <Msg state={state} />
    </form>
  );
}
