// 서버 API가 돌려줄 모양. 더미 백엔드와 실제 백엔드가 같은 타입을 쓴다.

/** 차가 보내는 타일 한 칸의 주행 결과. 원본 좌표는 차 밖으로 나가지 않는다. */
export interface TileSegment {
  id: string;
  hardBrake: boolean; // 이 타일 안에서 급제동
  warning: boolean; // 이 타일 안에서 차량 안전 경고
  assisted: boolean; // 에코 모드 또는 크루즈 주행
}

export interface TripInput {
  startedAt: string;
  endedAt: string;
  km: number;
  safetyScore: number; // 0~100, 차 안에서 계산
  hardBrakes: number;
  warnings: number;
  laneChanges: number;
  signaledLaneChanges: number;
  tiles: TileSegment[];
}

export interface Trip {
  id: string;
  endedAt: string;
  km: number;
  safetyScore: number;
  hardBrakes: number;
  warnings: number;
  laneChanges: number;
  signaledLaneChanges: number;
  tiles: TileSegment[];
  newTiles: number; // 처음 지나가 구매할 수 있게 된 타일
  credits: number; // 실제로 받은 크레딧(일일 상한 적용 뒤)
  breakdown: { clean: number; assistBonus: number; mannerBonus: number; excluded: number };
  capReached: boolean;
  rejected: boolean; // 기록 검증 실패로 보상 없음
}

export interface Tile {
  id: string;
  /** 지나간 날짜(YYYY-MM-DD, 서울 기준). 홈 루트 판정에 쓴다. */
  visitDays: string[];
  level: number; // 0이면 아직 사지 않은 땅
  collectedAt: string | null; // 마지막 수익 수령 시각
  special: string | null; // 특별 구간 이름
}

export interface TileView extends Tile {
  owned: boolean;
  homeRoute: boolean;
  incomePerHour: number;
  pending: number;
  buyCost: number | null; // 살 수 있으면 가격
  upgradeCost: number | null; // 올릴 수 있으면 가격
}

export interface Records {
  safeStreak: number; // 안전 점수 90점 이상 연속 주행
  bestSafeStreak: number;
  recentScores: number[];
  ownedTiles: number;
  specials: { id: string; name: string; collected: boolean }[];
}
