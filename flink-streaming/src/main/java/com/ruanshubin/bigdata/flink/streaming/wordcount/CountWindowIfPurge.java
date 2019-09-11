package com.ruanshubin.bigdata.flink.streaming.wordcount;

import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.WindowedStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.GlobalWindows;
import org.apache.flink.streaming.api.windowing.triggers.CountTrigger;
import org.apache.flink.streaming.api.windowing.triggers.PurgingTrigger;
import org.apache.flink.streaming.api.windowing.windows.GlobalWindow;
import org.apache.flink.util.Collector;

public class CountWindowIfPurge {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        env.setParallelism(1);

        DataStreamSource<String> source = env.socketTextStream("10.194.227.210", 9000, "\n");

        KeyedStream<Tuple2<String, Integer>, Tuple> keyedStream = source.flatMap(new FlatMapFunction<String, Tuple2<String, Integer>>() {

            @Override
            public void flatMap(String value, Collector<Tuple2<String, Integer>> collector) throws Exception {
                String[] strings = value.split("\\s+");
                for (String string : strings) {
                    collector.collect(new Tuple2<>(string, 1));
                }
            }
        }).keyBy(0);

        WindowedStream<Tuple2<String, Integer>, Tuple, GlobalWindow> countWindowWithoutPurge = keyedStream.window(GlobalWindows.create()).trigger(CountTrigger.of(2));

        WindowedStream<Tuple2<String, Integer>, Tuple, GlobalWindow> countWindowWithPurge = keyedStream.window(GlobalWindows.create()).trigger(PurgingTrigger.of(CountTrigger.of(2)));

        countWindowWithoutPurge.sum(1).print();

        countWindowWithPurge.sum(1).print();

        env.execute("Flink Count Window Demo");
    }
}
