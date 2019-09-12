## 分配Timestamps

为了让event time工作，Flink需要知道事件的时间戳，这意味着流中的每个元素都需要分配其事件时间戳。这个通常是通过抽取或者访问事件中某些字段的时间戳来获取的。

时间戳的分配伴随着水印的生成，告诉系统事件时间中的进度。

这里有两种方式来分配时间戳和生成水印:

- 直接在数据流源中进行；
- 通过timestamp assigner和watermark generator生成。

### Source Function with Timestamps And Watermarks

数据流源可以直接为它们产生的数据元素分配timestamp，并且他们也能发送水印。这样做的话，就没必要再去定义timestamp分配器了，需要注意的是:如果一个timestamp分配器被使用的话，由源提供的任何timestamp和watermark都会被重写。

为了通过源直接为一个元素分配一个timestamp，源需要调用SourceContext中的collectWithTimestamp(...)方法。为了生成watermark，源需要调用emitWatermark(Watermark)方法。

### TimestampAssigner

Timestamp分配器获取一个流并生成一个新的带有Timestamp元素和水印的流。如果原始流已经有时间戳和/或水印，则Timestamp分配程序将覆盖它们

Timestamp分配器通常在数据源之后立即指定，但这并不是严格要求的。通常是在timestamp分配器之前先解析(MapFunction)和过滤(FilterFunction)。在任何情况下，都需要在事件时间上的第一个操作(例如第一个窗口操作)之前指定timestamp分配程序。有一个特殊情况，当使用Kafka作为流作业的数据源时，Flink允许在源内部指定timestamp分配器和watermark生成器。更多关于如何进行的信息请参考Kafka Connector的文档。


- AssignerWithPeriodicWatermarks

AssignerWithPeriodicWatermarks分配时间戳并定期生成水印(这可能依赖于流元素，或者纯粹基于处理时间)。

watermark生成的时间间隔(每n毫秒)是通过ExecutionConfig.setAutoWatermarkInterval(…)定义的。每次调用分配器的getCurrentWatermark()方法时，如果返回的watermark非空且大于前一个watermark，则会发出新的watermark。

- AssignerWithPunctuatedWatermarks

无论何时，当某一事件表明需要创建新的watermark时，使用AssignerWithPunctuatedWatermarks创建。这个类首先调用extractTimestamp(…)方法来为元素分配一个时间戳，然后立即调用该元素上的checkAndGetNextWatermark(…)方法。

checkAndGetNextWatermark(…)方法传入在给extractTimestamp(…)方法中分配的timestamp，并可以决定是否要生成watermark。每当checkAndGetNextWatermark(…)方法返回一个非空watermark并且该watermark大于最新的前一个watermark时，就会发出新的watermark。

**注意: 可以在每个事件上生成一个watermark。但是，由于每个watermark都会导致下游的一些计算，过多的watermark会降低性能。**

