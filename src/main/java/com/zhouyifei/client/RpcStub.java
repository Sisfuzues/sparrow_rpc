package com.zhouyifei.client;


import com.zhouyifei.api.RpcRequest;
import com.zhouyifei.config.ConfigManager;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.Socket;


/**
 * 客户端使用的服务代理
 */
@Slf4j(topic =  "c.stub")
public class RpcStub implements InvocationHandler {
    @SuppressWarnings("unchecked")
    public <T> T getProxy(Class<T> interfaceClass){
        return (T) Proxy.newProxyInstance(
               interfaceClass.getClassLoader(),
               new Class<?>[]{interfaceClass},
                this
        );
    }

    @Override
    public Object invoke(Object o, Method method, Object[] objects) throws Throwable {
        String interfaceName = method.getDeclaringClass().getName();
        String methodName = method.getName();
        RpcRequest request;
        if(objects==null){
            request = new RpcRequest(interfaceName, methodName,new Class[0], new Object[0]);
        }else{
            Class<?>[] parameterTypes = method.getParameterTypes();
            Object[] args = new Object[objects.length];
            System.arraycopy(objects, 0, args, 0, objects.length);
            request = new RpcRequest(interfaceName, methodName, parameterTypes, args);
        }

        log.info("客户端准备发起 RPC 调用:{}",request.methodName());

        Object res = null;
        ConfigManager config = ConfigManager.getInstance();
        String ip = config.getRpcIp();
        int port = config.getRpcPort();

        log.info("链接微服务器 -> Ip:{} , Port:{}",ip,port);
        try(Socket socket = new Socket(ip,port);
            OutputStream os = socket.getOutputStream();
            ObjectOutputStream oss = new ObjectOutputStream(os);
            InputStream is = socket.getInputStream();
            ObjectInputStream ois = new ObjectInputStream(is);
            ){

            oss.writeObject(request);
            oss.flush();
            res = ois.readObject();
        }catch (IOException e){
            log.error("客户端链接异常:",e);
            throw new RuntimeException(e);
        }

        return  res;
    }
}
