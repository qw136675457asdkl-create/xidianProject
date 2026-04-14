package com.ruoyi.Xidian.utils;

import com.ruoyi.common.utils.SecurityUtils;

public class NickNameUtil {
    public static String getNickName(){
        return SecurityUtils.getLoginUser().getUser().getNickName();
    }
}
