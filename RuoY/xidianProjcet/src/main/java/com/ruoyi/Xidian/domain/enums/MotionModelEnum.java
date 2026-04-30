package com.ruoyi.Xidian.domain.enums;

public enum MotionModelEnum {

    STRAIGHT("straight", "直线轨迹"),
    QUADRATIC("quadratic", "二次曲线轨迹（抛物线形转弯）"),
    CUBIC("cubic", "三次曲线轨迹（S形转弯）"),
    TWO_SEGMENT("two_segment", "二折线轨迹（一个折点）"),
    THREE_SEGMENT("three_segment", "三折线轨迹（两个折点）");

    private final String code;
    private final String desc;

    MotionModelEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
