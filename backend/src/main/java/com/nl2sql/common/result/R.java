package com.nl2sql.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一返回结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class R<T> {
    private int code;
    private String message;
    private T data;

    public static <T> R<T> ok() { return new R<>(0, "success", null); }
    public static <T> R<T> ok(T data) { return new R<>(0, "success", data); }
    public static <T> R<T> fail(int code, String msg) { return new R<>(code, msg, null); }
    public static <T> R<T> fail(String msg) { return new R<>(500, msg, null); }
}
