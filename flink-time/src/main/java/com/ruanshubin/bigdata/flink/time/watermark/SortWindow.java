package com.ruanshubin.bigdata.flink.time.watermark;

import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.functions.windowing.WindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class SortWindow implements WindowFunction<Tuple2<String, Long>, Object, Tuple, TimeWindow> {
    @Override
    public void apply(Tuple tuple, TimeWindow timeWindow, Iterable<Tuple2<String, Long>> input, Collector<Object> out) throws Exception {
        String key = tuple.toString();
        //将input的时间戳取出，放入arrayList，并进行排序
        List<Long> arrayList = new ArrayList<Long>();
        Iterator<Tuple2<String, Long>> iterator = input.iterator();
        while (iterator.hasNext()) {
            Tuple2<String, Long> next = iterator.next();
            arrayList.add(next.f1);
        }
        Collections.sort(arrayList);
        //格式化时间戳
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

        String result = key + "," + arrayList.size() + "," + sdf.format(arrayList.get(0)) + "," + sdf.format(arrayList.get(arrayList.size() - 1))
                + "," + sdf.format(timeWindow.getStart()) + "," + sdf.format(timeWindow.getEnd());
        out.collect(result);
    }
}
