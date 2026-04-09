package com.zhouyifei.core.server;

import com.zhouyifei.api.RpcRequest;
import com.zhouyifei.api.RpcResponse;
import com.zhouyifei.api.annotation.Monitor;
import com.zhouyifei.core.config.ConfigManager;
import com.zhouyifei.core.monitor.core.MonitorRegistry;
import com.zhouyifei.core.monitor.proxy.MonitorProxy;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Monitor
@Slf4j(topic = "c.server")
public class RpcServer implements RpcServerMBean{
    private volatile boolean isStop = true;
    private volatile int port;

    private final HashMap<String,Object> serviceMap = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock(true);
    private ThreadPoolExecutor localThreadPool;

    public void register(Class<?> interfaceClass,Object serverInstance){
        lock.lock();
        try {
            Object proxy = Proxy.newProxyInstance(
                    interfaceClass.getClassLoader(),
                    new Class<?>[]{interfaceClass},
                    new MonitorProxy(serverInstance)
            );
            serviceMap.put(interfaceClass.getName(),proxy);
        } finally {
            lock.unlock();
        }
    }

    public void start(int port){
        localThreadPool = new ThreadPoolExecutor(
                ConfigManager.getInstance().getCoreSize(),
                ConfigManager.getInstance().getMaxSize(),
                60,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(ConfigManager.getInstance().getQueueCapacity()),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        try(ServerSocket serverSocket = new ServerSocket(port)){
            this.port = port;
            log.info("正在监听端口{}",port);
            while(isStop) {
                Socket socket = serverSocket.accept();

                localThreadPool.submit(() -> handleConnect(socket)) ;
            }
        } catch (IOException e) {
            log.error("初始化字接套异常。异常状况：",e);
            throw new RuntimeException(e);
        } finally {
            localThreadPool.shutdown();
        }
    }

    public void handleConnect(Socket socket){
        try(socket;
            InputStream is = socket.getInputStream();
            ObjectInputStream ois = new ObjectInputStream(is);
            OutputStream os = socket.getOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(os)){

            RpcResponse response = new RpcResponse();
            String requestId = null;

            try{
                Object obj = ois.readObject();

                if(!(obj instanceof RpcRequest rpcRequest)) {
                    log.error("对象类型异常，不属于RpcRequest类。");
                    response = RpcResponse.error(null,new RuntimeException("错误通信格式"),"通用错误格式。");
                    oos.writeObject(response);
                    oos.flush();
                    return;
                }

                log.info("收到来自{}的Rpc请求。",socket.getInetAddress());
                requestId = rpcRequest.requestId();
                String interfaceName = rpcRequest.interfaceName();
                String methodName = rpcRequest.methodName();
                Class<?>[] parameterTypes = rpcRequest.parameterTypes();
                Object[] args = rpcRequest.params();

                Object serviceInterface = serviceMap.get(interfaceName);
                if(serviceInterface==null) {
                    log.error("{}实例不存在。",interfaceName);
                    String exception = String.format("%s实例不存在。",interfaceName);
                    response = RpcResponse.error(requestId,new RuntimeException(exception),"实例不存在,请查看API。");
                    oos.writeObject(response);
                    oos.flush();
                    return;
                }

                Method serviceMethod = serviceInterface.getClass().getMethod(
                        methodName,parameterTypes
                );

                Object res = serviceMethod.invoke(serviceInterface,args);
                response = RpcResponse.success(requestId,res);
            } catch (Exception e){
                response = RpcResponse.error(requestId,new RuntimeException(e),"出现运行时错误，请查看错误信息。");
                log.error("出现错误：",e);
            }

            oos.writeObject(response);
            oos.flush();
        } catch (IOException e) {
            log.error("网络链接错误，请检查。具体信息:",e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public Long getCallCount() {
        return  MonitorRegistry.
                getInstance().getLocalMap().values().stream()
                .mapToLong(MonitorRegistry.MethodStatus::getCallCount)
                .sum();
    }

    @Override
    public Long getTotalTime() {
        return MonitorRegistry.
                getInstance().getLocalMap().values().stream()
                .mapToLong(MonitorRegistry.MethodStatus::getTotalTime)
                .sum();
    }

    @Override
    public Boolean getRunningType() {
        return isStop;
    }

    @Override
    public void stopRunning() {
        log.info("RpcServer 暂停服务。");
        isStop = false;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public double getAvgTime() {
        Long count = this.getCallCount();
        return count==0?0:(double) this.getTotalTime()/count;
    }

    @Override
    public Map<String, String> getMethodStatistics() {
        Map<String,String> res = new HashMap<>();

        MonitorRegistry.getInstance().getLocalMap().forEach((methodName,status)->{
                long curCount = status.getCallCount();
                long curTotalTime = status.getTotalTime();
                double curAvgTime = status.getAvgTime();
                String curInfo = String
                        .format("调用次数 : %d | 调用总耗时 : %dms | 平均调用耗时 : %.2fms"
                                ,curCount,curTotalTime,curAvgTime);

                res.put(methodName,curInfo);
        });

        if(res.isEmpty()){
            res.put("暂无数据","可能服务刚启动，无方法被调用。");
        }
        return res;
    }

    @Override
    public int getThreadNum() {
        return localThreadPool.getActiveCount();
    }

    @Override
    public int getBlockingQueue() {
        return localThreadPool.getQueue().size();
    }

    @Override
    public Long getTotalDone() {
        return localThreadPool.getCompletedTaskCount();
    }

    @Override
    public String getThreadInfo() {
        if(localThreadPool==null){
            return "服务未启动。";
        }
        int corePoolSize = localThreadPool.getCorePoolSize();
        int maximumPoolSize = localThreadPool.getMaximumPoolSize();
        return String.format("""
                线程池基本信息：
                | 核心线程数 | 最大线程数 | 阻塞队列大小 | 总共解决任务数 | 活跃线程数量 | 当前阻塞队列长度 |
                -------------------------------------------------------------------------------------
                | %d        | %d        | %d          | %d           | %d          | %d             |
                """,corePoolSize,maximumPoolSize,ConfigManager.getInstance().getQueueCapacity(),
                getTotalDone(),getThreadNum(),getBlockingQueue());
    }
}
