package com.zhouyifei.core.server;

import java.util.Map;

public interface RpcServerMBean {
    Long getCallCount();

    Long getTotalTime();

    Boolean getRunningType();

    void stopRunning();

    int getPort();

    double getAvgTime();

    Map<String,String> getMethodStatistics();
}