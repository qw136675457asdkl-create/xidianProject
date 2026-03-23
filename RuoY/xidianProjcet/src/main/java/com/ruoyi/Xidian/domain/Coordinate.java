package com.ruoyi.Xidian.domain;

import java.math.BigDecimal;

//坐标值对象
public class Coordinate {
    private BigDecimal longitude;
    private BigDecimal latitude;
    private BigDecimal altitude;

    public Coordinate() {
    }
    public Coordinate(BigDecimal longitude, BigDecimal latitude, BigDecimal altitude) {
        this.longitude = longitude;
        this.latitude = latitude;
        this.altitude = altitude;
    }
    // getter/setter
    public BigDecimal getLongitude() {
        return longitude;
    }
    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }
    public BigDecimal getLatitude() {
        return latitude;
    }
    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }
    public BigDecimal getAltitude() {
        return altitude;
    }
    public void setAltitude(BigDecimal altitude) {
        this.altitude = altitude;
    }
}