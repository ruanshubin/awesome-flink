package com.ruanshubin.bigdata.flink.time.watermark;

import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.functions.AssignerWithPeriodicWatermarks;
import org.apache.flink.streaming.api.watermark.Watermark;

import javax.annotation.Nullable;
import java.text.SimpleDateFormat;

/**
 * 周期性水印生成器
 */
public class PeriodicWatermarksProducer implements AssignerWithPeriodicWatermarks<Tuple2<String, Long>> {

    // 设置当前时间
    Long currentMaxTimestamp = 0L;
    // 设置最大的乱序时间
    final Long outOrderTime;

    public PeriodicWatermarksProducer(Long outOrderTime) {
        this.outOrderTime = outOrderTime;
    }

    // 格式化时间戳
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * 定义生产watermark的逻辑
     * 默认100ms被调用1次
     * @return
     */
    @Nullable
    @Override
    public Watermark getCurrentWatermark() {
        return new Watermark(currentMaxTimestamp - outOrderTime);
    }

    @Override
    public long extractTimestamp(Tuple2<String, Long> value, long l) {
        // 抽取timestamp
        Long timestamp = value.f1;
        currentMaxTimestamp = Math.max(timestamp, currentMaxTimestamp);
        Long id = Thread.currentThread().getId();
        //打印线程id，当前时间、event time、watermark
        System.out.println("当前线程：" + id + ",value: " + value.f0 + ",时间：\n\tcurrentMaxTime:[" + currentMaxTimestamp + "--" +
                sdf.format(currentMaxTimestamp) + "];\n\teventTime:[" + timestamp + "--" + sdf.format(timestamp) + "];\n\twatermark:[" +
                getCurrentWatermark().getTimestamp() + "--" + sdf.format(getCurrentWatermark().getTimestamp()) + "]");
        //返回event time
        return timestamp;
    }
}
