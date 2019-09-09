package com.ruanshubin.bigdata.flink.state;

import com.ruanshubin.bigdata.flink.source.RandomStringSource;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.BroadcastStream;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.BroadcastProcessFunction;
import org.apache.flink.util.Collector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 利用广播流实现敏感词的实时过滤
 */
public class SensitiveWordFiltering {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // 设置并行度
        env.setParallelism(1);

        // 自定义广播流，产生敏感词的配置信息
        List<String> sensitiveWordList = new ArrayList<>();
        sensitiveWordList = Arrays.asList("java", "python", "scala");
        DataStreamSource<String> sensitiveWord = env.addSource(new RandomStringSource(sensitiveWordList, 60L));
        BroadcastStream<String> sensitiveConfig = sensitiveWord.setParallelism(1).broadcast(new MapStateDescriptor<String, String>("sensitiveConfig", BasicTypeInfo.STRING_TYPE_INFO, BasicTypeInfo.STRING_TYPE_INFO));

        // 定义测试数据集
        List<String> data = Arrays.asList("java代码量太大",
                "python代码量少，易学习",
                "php是web开发语言",
                "scala流式处理语言，主要应用于大数据开发场景",
                "go是一种静态强类型、编译型、并发型，并具有垃圾回收功能的编程语言");
        DataStreamSource<String> dataStream = env.addSource(new RandomStringSource(data, 3L));

        // 数据流connent
        DataStream<String> result = dataStream.connect(sensitiveConfig).process(new BroadcastProcessFunction<String, String, String>() {
            // 拦截的敏感词
            private String keyword = null;

            @Override
            public void open(Configuration parameters) throws Exception {
                super.open(parameters);
                // 初始化敏感词
                keyword = "java";
                System.out.println("初始化敏感词: " + keyword);
            }

            @Override
            public void processElement(String value, ReadOnlyContext ctx, Collector<String> out) throws Exception {
                // 如果包含敏感词汇，则将敏感词汇替换为"***"
                if (value.contains(keyword)) {
                    out.collect("下游接收的消息为：" + value.replace(keyword, "***"));
                }else{
                    out.collect("下游接收的消息为：" + value);
                }
            }

            @Override
            public void processBroadcastElement(String value, Context ctx, Collector<String> out) throws Exception {
                // 更新敏感词
                keyword = value;
                System.out.println("更新敏感词: " + value);
            }
        });

        result.print();

        env.execute("Sensitive Word Filtering Job");

    }

}
