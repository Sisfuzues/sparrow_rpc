package com.zhouyifei.core.monitor.proxy;

import com.zhouyifei.api.annotation.Monitor;
import com.zhouyifei.core.monitor.core.MonitorRegistry;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class MonitorProxy implements InvocationHandler {
    private final Object realWorker;

    public MonitorProxy(Object realWorker){this.realWorker = realWorker;}

    @Override
    public Object invoke(Object o, Method method, Object[] args) throws Throwable {
        Method realMethod = realWorker.getClass()
                .getMethod(method.getName(),method.getParameterTypes());
        if(realMethod.isAnnotationPresent(Monitor.class)){
            long beginTime = System.currentTimeMillis();
            try{
                return method.invoke(realWorker,args);
            } catch (Exception e){
                //TODO 如果有异常，可以记录到检测类的异常管理中
            } finally {
                long cost = System.currentTimeMillis() - beginTime;
                String key = realWorker.getClass().getSimpleName()
                        + "." + realMethod.getName();
                MonitorRegistry.getInstance().report(key,cost);
            }
        }
        return method.invoke(realWorker,args);
    }
}
