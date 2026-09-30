/** 방 설정 선택지 (방 만들기·대기실 설정 변경이 같이 쓴다). 서버 RoomSettings 검증 범위 안의 값만 둔다 */
export const MAX_PLAYER_OPTIONS = [2, 3, 4, 5, 6]
export const STARTING_HP_OPTIONS = [100, 200, 300, 500]
export const TURN_TIME_OPTIONS = [15, 25, 40]

/** 체력 상한: 기본 500, 시작 체력이 더 크면 시작 체력 */
export const hpCapFor = (startingHp: number) => Math.max(500, startingHp)
