package com.ruoyi.Xidian.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class FileSizeUtil {

    private static final BigDecimal UNIT = BigDecimal.valueOf(1024);

    /**
     * 自动格式化文件大小
     * @param fileSize 数据库查询出的文件大小，单位：B
     * @return 例如：500 B、1.25 KB、10.5 MB、2.3 GB
     */
    public static String formatFileSize(Object fileSize) {
        if (fileSize == null) {
            return "0 B";
        }

        BigDecimal size;
        try {
            size = new BigDecimal(fileSize.toString());
        } catch (Exception e) {
            return "0 B";
        }

        if (size.compareTo(BigDecimal.ZERO) <= 0) {
            return "0 B";
        }

        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;

        while (size.compareTo(UNIT) >= 0 && unitIndex < units.length - 1) {
            size = size.divide(UNIT, 4, RoundingMode.DOWN);
            unitIndex++;
        }

        // 保留两位小数，直接截取，不四舍五入
        size = size.setScale(2, RoundingMode.DOWN);

        // 去掉多余的 .00
        String result = size.stripTrailingZeros().toPlainString();

        return result + " " + units[unitIndex];
    }
}