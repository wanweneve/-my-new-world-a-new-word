package com.arknights.stamina.skland;

/** 森空岛/鹰角接口层异常，message 面向用户（中文）。 */
public class SklandException extends Exception {
    public SklandException(String message) {
        super(message);
    }
}
