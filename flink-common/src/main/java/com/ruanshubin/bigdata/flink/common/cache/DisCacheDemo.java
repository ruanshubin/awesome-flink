package com.ruanshubin.bigdata.flink.common.cache;

import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.operators.DataSource;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.util.FileUtils;
import java.io.File;

/**
 * 分布式缓存的使用
 */
public class DisCacheDemo {

    public static void main(String[] args) throws Exception {
        // 获取运行环境
        ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();
        String cachePath = DisCacheDemo.class.getClassLoader().getResource("cache.txt").getPath();
        // 注册一个文件 本地文件或者分布式文件
        env.registerCachedFile(cachePath, "cache");

        DataSource<String> data = env.fromElements("a", "b", "c", "d");

        DataSet<String> result = data.map(new RichMapFunction<String, String>() {

            String cacheString;

            @Override
            public void open(Configuration parameters) throws Exception {
                super.open(parameters);
                File cache = getRuntimeContext().getDistributedCache().getFile("cache");
                cacheString = FileUtils.readFileUtf8(cache);

            }

            @Override
            public String map(String value) throws Exception {
                return cacheString + ": " + value;
            }
        });

        result.print();

    }
}
