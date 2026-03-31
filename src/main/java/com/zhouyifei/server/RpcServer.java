package com.zhouyifei.server;

import com.zhouyifei.api.RpcRequest;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

@Slf4j(topic = "c.server")
public class RpcServer {
    private HashMap<String,Object> serviceMap = new HashMap<>();

    public void register(String interfaceName,Object serverInstance){
        serviceMap.put(interfaceName,serverInstance);
    }

    public void start(int port){
        try(ServerSocket serverSocket = new ServerSocket(port)){
            log.info("正在监听端口{}",port);
            while(true) {
                Socket socket = serverSocket.accept();
                handleConnect(socket);
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
}
