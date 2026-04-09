package com.zhouyifei.api;

import lombok.Data;

import java.io.Serializable;

@Data
public class RpcResponse implements Serializable {
    private static final long serialVersion = 1L;

    private String requestId;
    private int code;
    private String message;
    private Object data;
    private Exception exception;

    public static RpcResponse success(String requestId,Object res){
        RpcResponse ans = new RpcResponse();
        ans.setCode(200);
        ans.setMessage("成功发送。");
        ans.setData(res);
        ans.setRequestId(requestId);
        return ans;
    }

    public static RpcResponse error(String requestId,Exception e,String message){
        RpcResponse ans = new RpcResponse();
        ans.setCode(500);
        ans.setRequestId(requestId);
        ans.setException(e);
        ans.setMessage(message);
        return ans;
    }
}
