package com.ruanshubin.bigdata.flink.window.top_speed;

import com.ruanshubin.bigdata.flink.source.CarSource;
import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.delta.DeltaFunction;
import org.apache.flink.streaming.api.windowing.assigners.GlobalWindows;
import org.apache.flink.streaming.api.windowing.evictors.TimeEvictor;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.triggers.DeltaTrigger;

import java.util.concurrent.TimeUnit;

/**
 * An example of grouped stream windowing where different eviction and trigger
 * policies can be used. A source fetches events from cars every 100 msec
 * containing their id, their current speed (kmh), overall elapsed distance (m)
 * and a timestamp. The streaming example triggers the top speed of each car
 * every x meters elapsed for the last y seconds.
 * 每100毫秒收集所有车辆的特征信息组成数据集，特征包含车辆的id、实时速度(km/h)、绝对距离(m)、时间戳。
 * 统计每x米前y秒内的最高速度。
 */
public class TopSpeedWindowing {
    public static void main(String[] args) throws Exception {
        final ParameterTool params = ParameterTool.fromArgs(args);

        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
        env.getConfig().setGlobalJobParameters(params);
        env.setParallelism(1);

        @SuppressWarnings({"rawtypes", "serial"})
        DataStream<Tuple4<Integer, Integer, Double, Long>> carData;
        if (params.has("input")) {
            carData = env.readTextFile(params.get("input")).map(new ParseCarData());
        } else {
            System.out.println("Executing TopSpeedWindowing example with default input data set.");
            System.out.println("Use --input to specify file input.");
            carData = env.addSource(CarSource.create(1));
        }

        //carData.print();

        int evictionSec = 10;
        double triggerMeters = 50;
        DataStream<Tuple4<Integer, Integer, Double, Long>> topSpeeds = carData
                .assignTimestampsAndWatermarks(new CarTimestamp())
                .keyBy(0)
                .window(GlobalWindows.create())
                // 窗口中只保留evictionSec秒的数据
                .evictor(TimeEvictor.of(Time.of(evictionSec, TimeUnit.SECONDS)))
                // 每triggerMeters米触发一次计算
                .trigger(DeltaTrigger.of(triggerMeters,
                        new DeltaFunction<Tuple4<Integer, Integer, Double, Long>>() {
                            private static final long serialVersionUID = 1L;

                            @Override
                            public double getDelta(
                                    Tuple4<Integer, Integer, Double, Long> oldDataPoint,
                                    Tuple4<Integer, Integer, Double, Long> newDataPoint) {
                                return newDataPoint.f2 - oldDataPoint.f2;
                            }
                        }, carData.getType().createSerializer(env.getConfig())))
                // 速度的最大值
                .maxBy(1);

        if (params.has("output")) {
            topSpeeds.writeAsText(params.get("output"));
        } else {
            System.out.println("Printing result to stdout. Use --output to specify output path.");
            topSpeeds.print();
        }

        env.execute("CarTopSpeedWindowingExample");
    }
}
