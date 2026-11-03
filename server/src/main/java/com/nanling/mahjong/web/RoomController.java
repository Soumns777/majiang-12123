package com.nanling.mahjong.web;

import com.nanling.mahjong.api.dto.TableView;
import com.nanling.mahjong.common.ApiResponse;
import com.nanling.mahjong.room.Room;
import com.nanling.mahjong.room.RoomService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 只读调试接口：对局主流程走 WebSocket，这里用于联调与运维排查。
 */
@RestController
@RequestMapping("/api")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of(
                "status", "UP",
                "rooms", roomService.listWaitingRooms().size()
        ));
    }

    @GetMapping("/rooms")
    public ApiResponse<List<Map<String, Object>>> rooms() {
        List<Map<String, Object>> data = roomService.listWaitingRooms().stream()
                .map(room -> Map.<String, Object>of(
                        "roomId", room.getId(),
                        "ownerId", room.getOwnerId(),
                        "players", room.getPlayers().size(),
                        "status", room.getStatus().name(),
                        "round", room.getRound(),
                        "maxRounds", room.getRuleConfig().getMaxRounds()))
                .toList();
        return ApiResponse.ok(data);
    }

    @GetMapping("/rooms/{roomId}")
    public ApiResponse<TableView> room(@PathVariable String roomId,
                                       @RequestParam String playerId) {
        return ApiResponse.ok(roomService.viewFor(roomId, playerId));
    }

    @GetMapping("/rooms/{roomId}/summary")
    public ApiResponse<Map<String, Object>> summary(@PathVariable String roomId) {
        Room room = roomService.requireRoom(roomId);
        return ApiResponse.ok(Map.of(
                "roomId", room.getId(),
                "status", room.getStatus().name(),
                "round", room.getRound(),
                "players", room.getPlayers().size(),
                "wildcard", room.getRuleConfig().getWildcardDisplay()
        ));
    }
}
