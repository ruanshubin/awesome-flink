package com.ruanshubin.bigdata.flink.source;

import org.apache.flink.streaming.api.functions.source.RichSourceFunction;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 随机字符串Source
 */
public class RandomStringSource extends RichSourceFunction<String> {

    // 数据集
    private List<String> values;
    // 时间间隔(单位为秒)
    private Long timeInterval;

    private boolean isRunning = true;

    public RandomStringSource(List<String> values, Long timeInterval) {
        this.values = values;
        this.timeInterval = timeInterval;
    }

    @Override
    public void run(SourceContext<String> ctx) throws Exception {
        int size = values.size();
        while(isRunning){
            TimeUnit.SECONDS.sleep(timeInterval);
            // 在数据集中随机生成1个发送
            int seed = (int) (Math.random() * size);
            ctx.collect(values.get(seed));
            System.out.println("上游发送的消息为: " + values.get(seed));
        }
    }

    @Override
    public void cancel() {
        isRunning = false;
    }
}
