// 캐릭터 등급 설정. 등급을 바꿀 때는 이 파일만 고친다.
// 웹·도감·차량 앱이 모두 여기서 계산한 값을 쓴다(차량 앱은 서버가 보내 준 이름과 크기를 그대로 그린다).

/** 진화 단계. 첫 단계는 진화 전 기본형이고, 이후 단계마다 도감 모습이 하나씩 생긴다. */
export const GRADES = [
  { label: "아기", fromLevel: 1 },
  { label: "어린이", fromLevel: 10 },
  { label: "청년", fromLevel: 30 },
  { label: "어른", fromLevel: 60 },
] as const;

/** 이 레벨에 닿으면 더 오르지 않고 먹이도 받지 않는다. */
export const MAX_LEVEL = 99;

/** 먹이 1개가 주는 경험치. */
export const XP_PER_FOOD = 5;

/** 다음 레벨까지 필요한 경험치. */
export function xpToNext(level: number) {
  return 40 + level * 10;
}
