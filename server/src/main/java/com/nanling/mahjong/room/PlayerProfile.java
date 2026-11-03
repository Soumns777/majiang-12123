package com.nanling.mahjong.room;

/**
 * 玩家档案（内存版）。
 *
 * <p>数据库接入后换成 user 表；目前只用于同一进程内的断线重连。</p>
 */
public record PlayerProfile(String id, String nickname, String avatar) {
}
