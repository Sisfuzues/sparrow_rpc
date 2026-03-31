package com.zhouyifei.config;

import lombok.Getter;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Getter
@Slf4j(topic = "c.Config")
public class ConfigManager {
    private static volatile ConfigManager instance;

    private final String rpcIp;
    private final int rpcPort;

    private ConfigManager() {
        Properties properties = new Properties();
        try(InputStream is = ConfigManager.class.getClassLoader().getResourceAsStream("rpc.properties")){
            if(is==null){
                log.error("配置文件地址异常，异常信息：");
                throw new RuntimeException("Config file not found.");
            }
            properties.load(is);

            this.rpcIp = properties.getProperty("rpc.server.ip","127.0.0.1");
            this.rpcPort = Integer.parseInt(properties.getProperty("rpc.server.port","8888"));

            log.info("配置加载成功 -> Ip: {} , Port: {}",this.rpcIp,this.rpcPort);
        }catch (IOException e){
            log.error("读取配置错误。错误信息：",e);
            throw new RuntimeException(e);
        }
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
