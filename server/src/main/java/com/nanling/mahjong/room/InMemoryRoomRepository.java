package com.nanling.mahjong.room;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 内存版房间仓储：数据库未接入前的默认实现。
 *
 * <p>注意：进程重启后房间全部丢失，仅用于联调与内测。</p>
 */
@Repository
@ConditionalOnProperty(name = "mahjong.storage.mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryRoomRepository implements RoomRepository {

    private final ConcurrentMap<String, Room> rooms = new ConcurrentHashMap<>();

    @Override
    public Optional<Room> findById(String roomId) {
        return Optional.ofNullable(rooms.get(roomId));
    }

    @Override
    public List<Room> findAll() {
        return List.copyOf(rooms.values());
    }

    @Override
    public void save(Room room) {
        rooms.put(room.getId(), room);
    }

    @Override
    public void delete(String roomId) {
        rooms.remove(roomId);
    }

    @Override
    public boolean exists(String roomId) {
        return rooms.containsKey(roomId);
    }
}
