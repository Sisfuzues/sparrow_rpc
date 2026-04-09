package com.zhouyifei.core.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Getter
@Slf4j(topic = "c.Config")
public class ConfigManager {
    private static volatile ConfigManager instance;

    /**
     * 配置的核心参数分别是：
     *  Rpc服务的 Ip
     *  Rpc服务的 端口
     *  RpcServer的线程池：
     *      核心大小
     *      最大支持大小
     *      等待队列大小
     */
    private final String rpcIp;
    private final int rpcPort;
    private final int coreSize;
    private final int maxSize;
    private final int queueCapacity;

    private ConfigManager() {
        Properties properties = new Properties();

            String defaultRpcIp = "127.0.0.1";
        int defaultRpcPort = 8888;
        int defaultRpcCoreSize = 16;
        int defaultRpcMaxSize = 100;
        int defaultQueueCapacity = 1000;

        try(InputStream is = ConfigManager.class.getClassLoader().getResourceAsStream("rpc.properties")){
            if(is==null){
                log.error("配置文件地址异常，将使用默认配置。");
            }else{
                properties.load(is);

                defaultRpcIp = properties.getProperty("rpc.server.ip","127.0.0.1");
                defaultRpcPort = Integer.parseInt(properties.getProperty("rpc.server.port","8888"));
                defaultRpcCoreSize = Integer.parseInt(properties.getProperty("rpc.server.core-size","16"));
                defaultRpcMaxSize = Integer.parseInt(properties.getProperty("rpc.server.max-size","100"));
                defaultQueueCapacity = Integer.parseInt(properties.getProperty("rpc.server.queue-capacity","1000"));

                log.info("配置加载成功 -> " +
                        "Ip: {} , Port: {}" +
                        "code-size: {}," +
                        "max-size: {}," +
                        "queue-capacity: {}",defaultRpcIp,defaultRpcPort,defaultRpcCoreSize,defaultRpcMaxSize,defaultQueueCapacity);
            }
        }catch (IOException e){
            log.error("读取配置错误,将使用默认配置。错误信息：",e);
        }

        this.rpcIp = defaultRpcIp;
        this.rpcPort = defaultRpcPort;
        this.coreSize = defaultRpcCoreSize;
        this.maxSize = defaultRpcMaxSize;
        this.queueCapacity = defaultQueueCapacity;
    }

    public static ConfigManager getInstance(){
        if(instance==null){
            synchronized (ConfigManager.class){
                if(instance==null) {
                    instance = new ConfigManager();
                }
            }
        }
        return instance;
    }
}
