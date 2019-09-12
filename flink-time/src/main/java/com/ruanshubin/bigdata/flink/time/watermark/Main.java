package com.ruanshubin.bigdata.flink.time.watermark;

import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.time.Time;

/**
 * 演示水印如何处理事件乱序的
 */
public class Main {

    // 服务器IP地址
    private static String hostname = "10.194.227.210";
    // 端口
    private static int port = 1000;
    // 分割符
    private static String delimiter = "\n";
    // 水印最大乱序时间
    private static Long outOrderTime = 5000L;

    /**
     * nc -l 1000
     * 样例数据：
     * Flink,1000
     * php,3000
     * Flink,9000
     * Hadoop,6000
     * Flink,12000
     * Python,15000
     * Flink,8000
     * Scala,5000
     * Flink,20000
     * Spark,21000
     * Flink,7000
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        //1、设置eventTime，默认是Processing Time
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
        //设置并行度
        env.setParallelism(1);
        //2、接收数据源
        DataStream<String> text = env.socketTextStream(hostname, port, delimiter);
        //3、解析数据：数据格式是：str,timestamp
        DataStream<Tuple2<String, Long>> inputMap = text.map(new MapFunction<String, Tuple2<String, Long>>() {
            public Tuple2<String, Long> map(String s) throws Exception {
                String[] values = s.split(",");
                return new Tuple2<String, Long>(values[0], Long.parseLong(values[1]));
            }
        });

        //4、抽取timestamp生成watermark
        DataStream<Tuple2<String, Long>> watermarkStrem = inputMap.assignTimestampsAndWatermarks(new PeriodicWatermarksProducer(outOrderTime));

        DataStream<Object> window = watermarkStrem.keyBy(0)
                .timeWindow(Time.seconds(10))
                .allowedLateness(Time.seconds(5)) // 当watermark >= window.time_end时，窗口会触发计算，但不会清除，当水位线watermark+5到达之前，分配到再次分配到窗口的元素均会触发窗口运算
                .apply(new SortWindow());

        window.print();

        env.execute("event time and watermark demo");
    }
}
