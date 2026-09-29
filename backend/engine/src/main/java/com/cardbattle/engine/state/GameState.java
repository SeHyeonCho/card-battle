package com.cardbattle.engine.state;

import com.cardbattle.engine.GameSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * 게임 한 판의 전체 상태. 서버는 이 객체를 통째로 JSON으로 Redis에 저장한다.
 *
 * <p>규칙 계산은 {@link com.cardbattle.engine.GameEngine}이 하고, 이 클래스는 값만 담는다.
 * players 리스트의 인덱스가 곧 좌석 번호다.
 */
public class GameState {

    private String gameId;
    private String packCode;
    private int packVersion;
    private GameSettings settings;
    private GameStatus status = GameStatus.IN_PROGRESS;
    private List<PlayerState> players = new ArrayList<>();
    /** +1 = 좌석 번호 증가 방향, -1 = 역방향 */
    private int direction = 1;
    private int currentSeat;
    private int turnNumber;
    /** 현재 공격력 A (PRD 7.1) */
    private int currentAttack;
    /** 누적 데미지 D (PRD 7.1) */
    private int accumulatedDamage;
    private List<FieldCard> field = new ArrayList<>();
    /** 필드 카드가 건 제출 제한 (PRD 7.7) */
    private List<FieldLock> fieldLocks = new ArrayList<>();
    /** 다음 턴 전환 때 건너뛸 차례 수 (점프) */
    private int pendingSkips;
    /** 다음 차례가 오면 아무것도 못 하고 넘어가는 플레이어 (시간아 멈춰라!) */
    private String lockedPlayerId;
    /** 다음 차례를 강제로 맡을 플레이어 (교차로) */
    private String forcedNextPlayerId;
    /** 설치된 랜덤시한폭탄. 없으면 null */
    private TimeBomb timeBomb;
    /** 무승부까지 남은 턴 수 (카페베네). 없으면 null */
    private Integer drawCountdown;
    /** 추가 제출 진행 중이면 그 상태, 아니면 null */
    private ExtraPlayState extraPlay;
    /** 게임에서 제외되어 더 이상 뽑히지 않는 카드 (블랙홀) */
    private List<String> bannedCardIds = new ArrayList<>();
    /** 상태 버전. 행동이 하나 처리될 때마다 1씩 오른다 (낙관적 동시성 제어) */
    private long version;
    /** 다음 이벤트 일련번호 */
    private long nextSeq = 1;
    /** 시드 기반 난수 상태 (같은 시드 + 같은 입력 = 같은 게임) */
    private long rngState;
    private int instanceCounter;
    private List<String> winnerIds = new ArrayList<>();
    /** 현재 턴 마감 시각 (epoch ms). 서버의 턴 타이머가 사용한다 */
    private long turnDeadlineEpochMs;

    public PlayerState player(String playerId) {
        for (PlayerState p : players) {
            if (p.getPlayerId().equals(playerId)) {
                return p;
            }
        }
        return null;
    }

    public PlayerState currentPlayer() {
        return players.get(currentSeat);
    }

    public List<PlayerState> alivePlayers() {
        return players.stream().filter(PlayerState::alive).toList();
    }

    public boolean inProgress() {
        return status == GameStatus.IN_PROGRESS;
    }

    public String newInstanceId() {
        instanceCounter++;
        return "c" + instanceCounter;
    }

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public String getPackCode() {
        return packCode;
    }

    public void setPackCode(String packCode) {
        this.packCode = packCode;
    }

    public int getPackVersion() {
        return packVersion;
    }

    public void setPackVersion(int packVersion) {
        this.packVersion = packVersion;
    }

    public GameSettings getSettings() {
        return settings;
    }

    public void setSettings(GameSettings settings) {
        this.settings = settings;
    }

    public GameStatus getStatus() {
        return status;
    }

    public void setStatus(GameStatus status) {
        this.status = status;
    }

    public List<PlayerState> getPlayers() {
        return players;
    }

    public void setPlayers(List<PlayerState> players) {
        this.players = players == null ? new ArrayList<>() : new ArrayList<>(players);
    }

    public int getDirection() {
        return direction;
    }

    public void setDirection(int direction) {
        this.direction = direction;
    }

    public int getCurrentSeat() {
        return currentSeat;
    }

    public void setCurrentSeat(int currentSeat) {
        this.currentSeat = currentSeat;
    }

    public int getTurnNumber() {
        return turnNumber;
    }

    public void setTurnNumber(int turnNumber) {
        this.turnNumber = turnNumber;
    }

    public int getCurrentAttack() {
        return currentAttack;
    }

    public void setCurrentAttack(int currentAttack) {
        this.currentAttack = currentAttack;
    }

    public int getAccumulatedDamage() {
        return accumulatedDamage;
    }

    public void setAccumulatedDamage(int accumulatedDamage) {
        this.accumulatedDamage = accumulatedDamage;
    }

    public List<FieldCard> getField() {
        return field;
    }

    public void setField(List<FieldCard> field) {
        this.field = field == null ? new ArrayList<>() : new ArrayList<>(field);
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public long getNextSeq() {
        return nextSeq;
    }

    public void setNextSeq(long nextSeq) {
        this.nextSeq = nextSeq;
    }

    public long getRngState() {
        return rngState;
    }

    public void setRngState(long rngState) {
        this.rngState = rngState;
    }

    public int getInstanceCounter() {
        return instanceCounter;
    }

    public void setInstanceCounter(int instanceCounter) {
        this.instanceCounter = instanceCounter;
    }

    public List<String> getWinnerIds() {
        return winnerIds;
    }

    public void setWinnerIds(List<String> winnerIds) {
        this.winnerIds = winnerIds == null ? new ArrayList<>() : new ArrayList<>(winnerIds);
    }

    public long getTurnDeadlineEpochMs() {
        return turnDeadlineEpochMs;
    }

    public void setTurnDeadlineEpochMs(long turnDeadlineEpochMs) {
        this.turnDeadlineEpochMs = turnDeadlineEpochMs;
    }

    public List<FieldLock> getFieldLocks() {
        return fieldLocks;
    }

    public void setFieldLocks(List<FieldLock> fieldLocks) {
        this.fieldLocks = fieldLocks == null ? new ArrayList<>() : new ArrayList<>(fieldLocks);
    }

    public int getPendingSkips() {
        return pendingSkips;
    }

    public void setPendingSkips(int pendingSkips) {
        this.pendingSkips = pendingSkips;
    }

    public String getLockedPlayerId() {
        return lockedPlayerId;
    }

    public void setLockedPlayerId(String lockedPlayerId) {
        this.lockedPlayerId = lockedPlayerId;
    }

    public String getForcedNextPlayerId() {
        return forcedNextPlayerId;
    }

    public void setForcedNextPlayerId(String forcedNextPlayerId) {
        this.forcedNextPlayerId = forcedNextPlayerId;
    }

    public TimeBomb getTimeBomb() {
        return timeBomb;
    }

    public void setTimeBomb(TimeBomb timeBomb) {
        this.timeBomb = timeBomb;
    }

    public Integer getDrawCountdown() {
        return drawCountdown;
    }

    public void setDrawCountdown(Integer drawCountdown) {
        this.drawCountdown = drawCountdown;
    }

    public ExtraPlayState getExtraPlay() {
        return extraPlay;
    }

    public void setExtraPlay(ExtraPlayState extraPlay) {
        this.extraPlay = extraPlay;
    }

    public List<String> getBannedCardIds() {
        return bannedCardIds;
    }

    public void setBannedCardIds(List<String> bannedCardIds) {
        this.bannedCardIds = bannedCardIds == null ? new ArrayList<>() : new ArrayList<>(bannedCardIds);
    }
}
