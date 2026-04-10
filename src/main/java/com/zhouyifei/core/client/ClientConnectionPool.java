package com.zhouyifei.core.client;

import com.zhouyifei.core.config.ConfigManager;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j(topic =  "c.ClientConnectionPool")
public class ClientConnectionPool {

    private static volatile ClientConnectionPool INSTANCE;

    private final ConcurrentHashMap<String,ArrayBlockingQueue<Socket>> socketMap
            = new ConcurrentHashMap<>();

    public static ClientConnectionPool getINSTANCE(){
        if(INSTANCE==null){
            synchronized (ClientConnectionPool.class){
                if(INSTANCE==null){
                    INSTANCE = new ClientConnectionPool();
                }
            }
        }
        return INSTANCE;
    }

    public Socket getSocket(String ip,int port){
        String key = ip + ":" + String.valueOf(port);
        ArrayBlockingQueue<Socket> socketAbq = socketMap.computeIfAbsent(key, k->{
            int n = ConfigManager.getInstance().getStubQueueCapacity();
            ArrayBlockingQueue<Socket> curAbq =
                    new ArrayBlockingQueue<Socket>(n);
            for (int i = 0; i < n; i++) {
                try {
                    Socket curSocket = new Socket(ip, port);
                    curAbq.add(curSocket);
                } catch (IOException e) {
                    log.error("初始化socket产生问题。");
                    throw new RuntimeException(e);
                }
            }
            return curAbq;
        });

        try{
            return socketAbq.take();
        } catch (InterruptedException e){
            log.error("获取socket异常",e);
            throw new RuntimeException(e);
        }
    }

    public void releaseSocket(String ip,int port,Socket socket){
        String key  = ip + ":" + String.valueOf(port);
        ArrayBlockingQueue<Socket> getAcq = socketMap.get(key);

        if(getAcq!=null && socket!=null && !socket.isClosed()){
            try{
                getAcq.put(socket);
            } catch (InterruptedException e) {
                log.error("放会阻塞队列失败：",e);
            }
        } else{
            log.warn("阻塞队列错误，或者字接套不存在或者断开链接了。");
        }
    }
}
