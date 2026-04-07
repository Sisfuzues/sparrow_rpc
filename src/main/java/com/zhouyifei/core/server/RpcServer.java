package com.zhouyifei.core.server;

import com.zhouyifei.api.RpcRequest;
import com.zhouyifei.api.annotation.Monitor;
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
import java.util.concurrent.locks.ReentrantLock;

@Monitor
@Slf4j(topic = "c.server")
public class RpcServer implements RpcServerMBean{
    private volatile boolean isStop = true;
    private volatile int port;

    private final HashMap<String,Object> serviceMap = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock(true);

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
        try(ServerSocket serverSocket = new ServerSocket(port)){
            this.port = port;
            log.info("正在监听端口{}",port);
            while(isStop) {
                Socket socket = serverSocket.accept();

                Thread t = new Thread(() -> handleConnect(socket),"serverThread");
                t.start();
            }
        } catch (IOException e) {
            log.error("初始化字接套异常。异常状况：",e);
            throw new RuntimeException(e);
        }
    }

    public void handleConnect(Socket socket){
        try(socket;
            InputStream is = socket.getInputStream();
            ObjectInputStream ois = new ObjectInputStream(is);
            OutputStream os = socket.getOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(os)){

            Object obj = ois.readObject();

            if(!(obj instanceof RpcRequest rpcRequest)) {
                log.error("对象类型异常，不属于RpcRequest类。");
                return;
            }

            log.info("收到来自{}的Rpc请求。",socket.getInetAddress());
            String interfaceName = rpcRequest.interfaceName();
            String methodName = rpcRequest.methodName();
            Class<?>[] parameterTypes = rpcRequest.parameterTypes();
            Object[] args = rpcRequest.params();

            Object serviceInterface = serviceMap.get(interfaceName);
            if(serviceInterface==null) {
                log.error("{}实例不存在。",interfaceName);
                return;
            }

            Method serviceMethod = serviceInterface.getClass().getMethod(
                    methodName,parameterTypes
            );

            Object res = serviceMethod.invoke(serviceInterface,args);
            oos.writeObject(res);
            oos.flush();

        } catch (ClassNotFoundException e) {
            log.error("读出对象异常。异常状况：",e);
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            log.error("对应对象异常，没有查找到相关方法。异常状况:",e);
            throw new RuntimeException(e);
        } catch (InvocationTargetException | IOException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            log.error("执行方法错误：",e);
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
}
