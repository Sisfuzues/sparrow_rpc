package com.zhouyifei.api;

import java.io.Serializable;

public record RpcRequest (
    String requestId,
    String interfaceName,
    String methodName,
    Class<?>[] parameterTypes,
    Object[] params
)implements Serializable {

}