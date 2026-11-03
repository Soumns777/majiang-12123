package com.nanling.mahjong;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 南陵麻将服务端入口。
 *
 * <p>当前版本（feature-1.0.0）使用内存存储，数据库接入后只需替换 RoomRepository 实现。</p>
 */
@EnableScheduling
@SpringBootApplication
public class MahjongApplication {

    public static void main(String[] args) {
        SpringApplication.run(MahjongApplication.class, args);
    }
}
