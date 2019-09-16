package com.ruanshubin.bigdata.flink.window.top_speed;

import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.streaming.api.functions.timestamps.AscendingTimestampExtractor;

public class CarTimestamp extends AscendingTimestampExtractor<Tuple4<Integer, Integer, Double, Long>> {
    @Override
    public long extractAscendingTimestamp(Tuple4<Integer, Integer, Double, Long> element) {
        return element.f3;
    }
}
