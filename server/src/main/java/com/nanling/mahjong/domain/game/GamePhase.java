package com.nanling.mahjong.domain.game;

/**
 * 对局阶段。
 */
public enum GamePhase {
    /** 未开始 / 等待准备 */
    WAITING,
    /** 轮到某位玩家出牌（摸牌后或碰杠后） */
    PLAYER_TURN,
    /** 有人打出牌后，等待其他玩家碰/杠/胡响应 */
    WAITING_CLAIM,
    /** 本局结束 */
    ENDED
}
