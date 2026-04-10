package com.zhouyifei.core.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Getter
@Slf4j(topic = "c.Config")
public class ConfigManager {
    private static volatile ConfigManager instance;

    /**
     * 配置的核心参数分别是：
     *  RpcIp集群Map:
     *      Rpc服务的 Ip
     *      Rpc服务的 端口
     *  RpcServer的线程池：
     *      核心大小
     *      最大支持大小
     *      等待队列大小
     *  RpcStub：
     *      阻塞队列长度
     */
    private final Map<String, ServiceAddress> serviceAddressMap = new HashMap<>();
    private final int coreSize;
    private final int maxSize;
    private final int queueCapacity;
    private final int stubQueueCapacity;

    public record ServiceAddress(String ip, int port){}

    private void initServiceAddressMap(Properties props){
        for(String key : props.stringPropertyNames()){
            if(key.startsWith("rpc.server.api")){
                String interfaceName = key.substring("rpc.server.api.".length());

                String address = props.getProperty(key);

                String[] ipPort = address.split(":");
                if(ipPort.length == 2){
                    String ip = ipPort[0];
                    int port = Integer.parseInt(ipPort[1]);

                    ServiceAddress serviceAddress = new ServiceAddress(ip, port);
                    serviceAddressMap.put(interfaceName,serviceAddress);
                }
            }
        }

    }

    public ServiceAddress getServiceAddress(String interfaceName){
        return serviceAddressMap.get(interfaceName);
    }

    private ConfigManager() {
        Properties properties = new Properties();

        int defaultRpcCoreSize = 16;
        int defaultRpcMaxSize = 100;
        int defaultQueueCapacity = 1000;
        int defaultStubQC = 10;

        try(InputStream is = ConfigManager.class.getClassLoader().getResourceAsStream("rpc.properties")){
            if(is==null){
                log.error("配置文件地址异常，将使用默认配置。");
            }else{
                properties.load(is);

                initServiceAddressMap(properties);
                defaultRpcCoreSize = Integer.parseInt(properties.getProperty("rpc.server.core-size","16"));
                defaultRpcMaxSize = Integer.parseInt(properties.getProperty("rpc.server.max-size","100"));
                defaultQueueCapacity = Integer.parseInt(properties.getProperty("rpc.server.queue-capacity","1000"));
                defaultStubQC = Integer.parseInt(properties.getProperty("rpc.stub.queue-capacity","10"));

                log.info("配置加载成功 -> " +
                        "code-size: {}," +
                        "max-size: {}," +
                        "queue-capacity: {}",defaultRpcCoreSize,defaultRpcMaxSize,defaultQueueCapacity);
            }
        }catch (IOException e){
            log.error("读取配置错误,将使用默认配置。错误信息：",e);
        }

        this.coreSize = defaultRpcCoreSize;
        this.maxSize = defaultRpcMaxSize;
        this.queueCapacity = defaultQueueCapacity;
        this.stubQueueCapacity = defaultStubQC;
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
