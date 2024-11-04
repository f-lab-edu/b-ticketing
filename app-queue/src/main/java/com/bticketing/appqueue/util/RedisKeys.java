package com.bticketing.appqueue.util;

public class RedisKeys {
    // 대기열 관련 키
    public static final String QUEUE_KEY = "active_queue";

    // 일일 통계 저장 키
    public static final String DAILY_STATS_KEY = "daily_queue_stats";

    // VIP 사용자 키 프리픽스
    private static final String VIP_KEY_PREFIX = "vip:";

    // 사용자 상태 키 프리픽스
    private static final String USER_STATUS_PREFIX = "user_status:";


    //VIP 사용자 키 생성
    public static String getVIPKey(String userId) {
        return VIP_KEY_PREFIX + userId;
    }

     //사용자 상태 키 생성
    public static String getUserStatusKey(String userToken) {
        return USER_STATUS_PREFIX + userToken;
    }
}
