package com.ruanshubin.bigdata.flink.window.top_speed;

import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.java.tuple.Tuple4;

public class ParseCarData extends RichMapFunction<String, Tuple4<Integer, Integer, Double, Long>> {
    private static final long serialVersionUID = 1L;

    @Override
    public Tuple4<Integer, Integer, Double, Long> map(String record) {
        String rawData = record.substring(1, record.length() - 1);
        String[] data = rawData.split(",");
        return new Tuple4<>(Integer.valueOf(data[0]), Integer.valueOf(data[1]), Double.valueOf(data[2]), Long.valueOf(data[3]));
    }
}
