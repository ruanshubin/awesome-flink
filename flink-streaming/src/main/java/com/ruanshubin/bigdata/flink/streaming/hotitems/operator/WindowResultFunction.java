package com.ruanshubin.bigdata.flink.streaming.hotitems.operator;

import com.ruanshubin.bigdata.flink.streaming.hotitems.entity.ItemViewCount;
import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.api.java.tuple.Tuple1;
import org.apache.flink.streaming.api.functions.windowing.WindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

/**
 * 用于输出窗口的结果
 */
public class WindowResultFunction implements WindowFunction<Long, ItemViewCount, Tuple, TimeWindow> {
    @Override
    public void apply(
            Tuple key, // 窗口的主键，即itemId
            TimeWindow timeWindow, // 窗口
            Iterable<Long> aggregateResult, // 聚合函数的结果，即count值
            Collector<ItemViewCount> collector // 输出类型为ItemViewCount
    ) throws Exception {
        Long itemId = ((Tuple1<Long>) key).f0;
        Long count = aggregateResult.iterator().next();
        collector.collect(ItemViewCount.of(itemId, timeWindow.getEnd(), count));
    }
}
