package com.zhouyifei.core.monitor.core;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j(topic = "c.MonitorRegistry")
public class MonitorRegistry {
    private static final MonitorRegistry INSTANCE = new MonitorRegistry();
    public static MonitorRegistry getInstance(){return INSTANCE;}

    private MonitorRegistry(){}

    private final Map<String, MethodStatus> localMap = new ConcurrentHashMap<>();

    public void report(String methodName, long cost) {
        log.info("方法 {} 被调用了，总共耗时 {}。",methodName,cost);
        localMap.computeIfAbsent(methodName,(k) -> new MethodStatus())
                .update(cost);
    }

    public Map<String, MethodStatus> getLocalMap(){
        return localMap;
    }

    public MethodStatus getMethodStatus(String methodName){
        return localMap.getOrDefault(methodName,new MethodStatus());
    }

    public static class MethodStatus {
        private final AtomicLong callCount = new AtomicLong(0L);
        private final AtomicLong totalTime = new AtomicLong(0L);
        public void update(long cost) {
            callCount.incrementAndGet();
            totalTime.addAndGet(cost);
        }

        public long getCallCount() {
            return callCount.get();
        }

        public long getTotalTime() {
            return totalTime.get();
        }

        public double getAvgTime(){
            long count = callCount.get();
            return count==0?0:(double) totalTime.get()/count;
        }
    }
}
