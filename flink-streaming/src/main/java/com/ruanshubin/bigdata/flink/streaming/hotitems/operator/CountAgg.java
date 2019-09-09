package com.ruanshubin.bigdata.flink.streaming.hotitems.operator;

import com.ruanshubin.bigdata.flink.streaming.hotitems.entity.UserBehavior;
import org.apache.flink.api.common.functions.AggregateFunction;

/**
 * Count统计聚合函数实现，每出现1条记录加1
 */
public class CountAgg implements AggregateFunction<UserBehavior, Long, Long> {
    @Override
    public Long createAccumulator() {
        return 0L;
    }

    @Override
    public Long add(UserBehavior userBehavior, Long acc) {
        return acc+1;
    }

    @Override
    public Long getResult(Long acc) {
        return acc;
    }

    @Override
    public Long merge(Long acc1, Long acc2) {
        return acc1 + acc2;
    }
}
