-- CS 饰品磨损值需要保留导出文件中的完整精度，4 位小数不足以区分具体饰品。
ALTER TABLE lots
    MODIFY float_value DECIMAL(21,19) NULL;
