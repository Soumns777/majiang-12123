package com.nanling.mahjong.room;

import java.util.List;
import java.util.Optional;

/**
 * 房间仓储抽象。
 *
 * <p>当前实现：{@link InMemoryRoomRepository}。
 * 服务器到位后新增 JPA/MyBatis 实现并切换 {@code mahjong.storage.mode} 即可，
 * 业务层不感知存储方式。</p>
 */
public interface RoomRepository {

    Optional<Room> findById(String roomId);

    List<Room> findAll();

    void save(Room room);

    void delete(String roomId);

    boolean exists(String roomId);
}
