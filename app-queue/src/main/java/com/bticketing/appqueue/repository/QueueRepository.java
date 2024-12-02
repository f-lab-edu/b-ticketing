package com.bticketing.appqueue.repository;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

public interface QueueRepository {
    Object getValue(String key);

    void setValue(String key, Object value);

    void setValueWithTTL(String key, Object value, Duration ttl);

    Long incrementValue(String key);

    Long getListLength(String key);

    void pushToList(String key, String value);

    List<String> popMultipleFromList(String key, int count);

    boolean acquireLock(String key, Duration ttl);

    void releaseLock(String key);

}
