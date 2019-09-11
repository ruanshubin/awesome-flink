## Window

![](Flink的Window源码剖析_files/1.jpg)

可以看到，GlobalWindow和TimeWindow均继承自抽象类Window，其源码如下：

```
public abstract class Window {
	public abstract long maxTimestamp();
}
```

可以看出，Window抽象类仅有一个maxTimestamp()方法用于获取仍属于该窗口的最大时间戳。

### TimeWindow

首先看TimeWindow的数据结构：

```
public class TimeWindow extends Window {

	private final long start;
	private final long end;

	public TimeWindow(long start, long end) {
		this.start = start;
		this.end = end;
	}

	
	public long getStart() {
		return start;
	}

	public long getEnd() {
		return end;
	}

	@Override
	public long maxTimestamp() {
		return end - 1;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		TimeWindow window = (TimeWindow) o;

		return end == window.end && start == window.start;
	}

	@Override
	public int hashCode() {
		return MathUtils.longToIntWithBitMixing(start + end);
	}

	@Override
	public String toString() {
		return "TimeWindow{" +
				"start=" + start +
				", end=" + end +
				'}';
	}

	public boolean intersects(TimeWindow other) {
		return this.start <= other.end && this.end >= other.start;
	}

	public TimeWindow cover(TimeWindow other) {
		return new TimeWindow(Math.min(start, other.start), Math.max(end, other.end));
	}

	......
	
	public static void mergeWindows(Collection<TimeWindow> windows, MergingWindowAssigner.MergeCallback<TimeWindow> c) {

		List<TimeWindow> sortedWindows = new ArrayList<>(windows);

		Collections.sort(sortedWindows, new Comparator<TimeWindow>() {
			@Override
			public int compare(TimeWindow o1, TimeWindow o2) {
				return Long.compare(o1.getStart(), o2.getStart());
			}
		});

		List<Tuple2<TimeWindow, Set<TimeWindow>>> merged = new ArrayList<>();
		Tuple2<TimeWindow, Set<TimeWindow>> currentMerge = null;

		for (TimeWindow candidate: sortedWindows) {
			if (currentMerge == null) {
				currentMerge = new Tuple2<>();
				currentMerge.f0 = candidate;
				currentMerge.f1 = new HashSet<>();
				currentMerge.f1.add(candidate);
			} else if (currentMerge.f0.intersects(candidate)) {
				currentMerge.f0 = currentMerge.f0.cover(candidate);
				currentMerge.f1.add(candidate);
			} else {
				merged.add(currentMerge);
				currentMerge = new Tuple2<>();
				currentMerge.f0 = candidate;
				currentMerge.f1 = new HashSet<>();
				currentMerge.f1.add(candidate);
			}
		}

		if (currentMerge != null) {
			merged.add(currentMerge);
		}

		for (Tuple2<TimeWindow, Set<TimeWindow>> m: merged) {
			if (m.f1.size() > 1) {
				c.merge(m.f1, m.f0);
			}
		}
	}

	public static long getWindowStartWithOffset(long timestamp, long offset, long windowSize) {
		return timestamp - (timestamp - offset + windowSize) % windowSize;
	}
}
```

**该窗口主要用于实现时间驱动的相关操作**。

可以看到。TimeWindow由start和end2个时间戳组成，最大时间戳为end-1，同时，TimeWindow提供了getWindowStartWithOffset静态方法，用于获取时间戳所属时间窗的起点，其中的offset为偏移量。例如，没有偏移量的话，小时滚动窗口将按时间纪元来对齐，也就是1:00:00--1:59:59,2:00:00--2:59:59等，如果你指定了15分钟的偏移，你将得到1:15:00--2:14:59,2:15:00--3:14:59等。时间偏移主要用于调准非0时区的窗口，例如:在中国你需要指定8小时的时间偏移。

intersects方法用于判断2个时间窗是否有交集，cover方法用于求2个时间窗的合集，mergeWindows用于将时间窗集合进行合并，该方法是实现Session Window的关键。

对于session window来说，我们需要窗口变得更灵活。基本的思想是这样的：SessionWindows assigner 会为每个进入的元素分配一个窗口，该窗口以元素的时间戳作为起始点，时间戳加会话超时时间为结束点，也就是该窗口为[timestamp, timestamp+sessionGap)。比如我们现在到了两个元素，它们被分配到两个独立的窗口中，两个窗口目前不相交，如图：

![](Flink的Window源码剖析_files/2.jpg)

当第三个元素进入时，分配到的窗口与现有的两个窗口发生了叠加，情况变成了这样：

![](Flink的Window源码剖析_files/3.jpg)

由于我们支持了窗口的合并，WindowAssigner可以合并这些窗口。它会遍历现有的窗口，并告诉系统哪些窗口需要合并成新的窗口。Flink 会将这些窗口进行合并，合并的主要内容有两部分：

- 需要合并的窗口的底层状态的合并（也就是窗口中缓存的数据，或者对于聚合窗口来说是一个聚合值）；
- 需要合并的窗口的Trigger的合并（比如对于EventTime来说，会删除旧窗口注册的定时器，并注册新窗口的定时器）。

总之，结果是三个元素现在在同一个窗口中：

![](Flink的Window源码剖析_files/4.jpg)

需要注意的是，对于每一个新进入的元素，都会分配一个属于该元素的窗口，都会检查并合并现有的窗口。在触发窗口计算之前，每一次都会检查该窗口是否可以和其他窗口合并，直到trigger触发后，会将该窗口从窗口列表中移除。对于 event time 来说，窗口的触发是要等到大于窗口结束时间的 watermark 到达，当watermark没有到，窗口会一直缓存着。所以基于这种机制，可以做到对乱序消息的支持。

这里有一个优化点可以做，因为每一个新进入的元素都会创建属于该元素的窗口，然后合并。如果新元素连续不断地进来，并且新元素的窗口一直都是可以和之前的窗口重叠合并的，那么其实这里多了很多不必要的创建窗口、合并窗口的操作，我们可以直接将新元素放到那个已存在的窗口，然后扩展该窗口的大小，看起来就像和新元素的窗口合并了一样。

### GlobalWindow

接着看GlobalWindow：

```
public class GlobalWindow extends Window {

	private static final GlobalWindow INSTANCE = new GlobalWindow();

	private GlobalWindow() { }

	public static GlobalWindow get() {
		return INSTANCE;
	}

	@Override
	public long maxTimestamp() {
		return Long.MAX_VALUE;
	}

	@Override
	public boolean equals(Object o) {
		return this == o || !(o == null || getClass() != o.getClass());
	}

	@Override
	public int hashCode() {
		return 0;
	}

	@Override
	public String toString() {
		return "GlobalWindow";
	}

	/**
	 * 序列化相关操作
	 */
}

```

GlobalWindow提供了get()静态方法用于获取GlobalWindow实例，maxTimestamp()统一返回Long的最大值，而hashCode统一返回0。

**该窗口主要用于实现数据驱动的相关操作**。

## WindowAssigner

顾名思义，WindowAssigner用来决定某个元素被分配到哪个/哪些窗口中去。

```
public abstract class WindowAssigner<T, W extends Window> implements Serializable {
	private static final long serialVersionUID = 1L;

	public abstract Collection<W> assignWindows(T element, long timestamp, WindowAssignerContext context);

	public abstract Trigger<T, W> getDefaultTrigger(StreamExecutionEnvironment env);

	public abstract TypeSerializer<W> getWindowSerializer(ExecutionConfig executionConfig);

	public abstract boolean isEventTime();

	public abstract static class WindowAssignerContext {

		public abstract long getCurrentProcessingTime();

	}
}
```

首先看assignWindows方法，其输入element为待分配的元素，timestamp为element持有的时间戳，context为该分配器的上下文容器，返回值为element所属的窗口集合，也就说，同一条数据元素，可能会被分配到多个窗口中去。但并不是将该数据复制到多个窗口中去，Window本身只是一个ID标识符，其内部可能存储了一些元数据，如TimeWindow中有开始和结束时间，但是并不会存储窗口中的元素。窗口中的元素实际存储在 Key/Value State 中，key为Window，value为元素集合（或聚合值）。为了保证窗口的容错性，该实现依赖了 Flink 的 State 机制（参见 state 文档）。

接着看其他方法，getDefaultTrigger用于返回该窗口默认的触发器，getWindowSerializer用于返回窗口的序列化器，isEventTime用于判断是否基于event time来进行元素的分配。

最后看一下WindowAssignerContext这个上下文容器，其是一个内部静态抽象类，提供了getCurrentProcessingTime方法用于获取当前的processing time。

![](Flink的Window源码剖析_files/6.jpg)

![](Flink的Window源码剖析_files/5.jpg)

看一下WindowAssigner的实现类，GlobalWindows主要用于实现CountWindow，MergingWindowAssigner主要用于实现SessionWindow，剩下的分别基于processing time和event time实现了翻滚窗口和滑动窗口。

先捡软柿子捏，首先看较为简单的GlobalWindows：

### GlobalWindows

```
public class GlobalWindows extends WindowAssigner<Object, GlobalWindow> {
	private static final long serialVersionUID = 1L;

	private GlobalWindows() {}

	@Override
	public Collection<GlobalWindow> assignWindows(Object element, long timestamp, WindowAssignerContext context) {
		return Collections.singletonList(GlobalWindow.get());
	}

	@Override
	public Trigger<Object, GlobalWindow> getDefaultTrigger(StreamExecutionEnvironment env) {
		return new NeverTrigger();
	}

	@Override
	public String toString() {
		return "GlobalWindows()";
	}
	
	public static GlobalWindows create() {
		return new GlobalWindows();
	}

	@Internal
	public static class NeverTrigger extends Trigger<Object, GlobalWindow> {
		private static final long serialVersionUID = 1L;

		@Override
		public TriggerResult onElement(Object element, long timestamp, GlobalWindow window, TriggerContext ctx) {
			return TriggerResult.CONTINUE;
		}

		@Override
		public TriggerResult onEventTime(long time, GlobalWindow window, TriggerContext ctx) {
			return TriggerResult.CONTINUE;
		}

		@Override
		public TriggerResult onProcessingTime(long time, GlobalWindow window, TriggerContext ctx) {
			return TriggerResult.CONTINUE;
		}

		@Override
		public void clear(GlobalWindow window, TriggerContext ctx) throws Exception {}

		@Override
		public void onMerge(GlobalWindow window, OnMergeContext ctx) {
		}
	}

	@Override
	public TypeSerializer<GlobalWindow> getWindowSerializer(ExecutionConfig executionConfig) {
		return new GlobalWindow.Serializer();
	}

	@Override
	public boolean isEventTime() {
		return false;
	}
}
```

GlobalWindows提供了create静态方法用于返回GlobalWindows实例，assignWindows方法会将上游的元素全都分配到一个单例GlobalWindow中，其默认的Trigger为NeverTrigger，即永不触发fire计算。

### MergingWindowAssigner

```
public abstract class MergingWindowAssigner<T, W extends Window> extends WindowAssigner<T, W> {
	private static final long serialVersionUID = 1L;

	public abstract void mergeWindows(Collection<W> windows, MergeCallback<W> callback);

	public interface MergeCallback<W> {

		void merge(Collection<W> toBeMerged, W mergeResult);
	}
}
```

MergingWindowAssigner主要提供了MergeCallback抽象接口，然后将该接口传递给TimeWindow的mergeWindows方法来进行窗口的合并(具体可看TimeWindow)。

```
public static void mergeWindows(Collection<TimeWindow> windows, MergingWindowAssigner.MergeCallback<TimeWindow> c) {
	......
}
```

## Trigger

触发器。决定了一个窗口何时能够被计算或清除，每个窗口都会拥有一个自己的Trigger。

```
public abstract class Trigger<T, W extends Window> implements Serializable {
	
	private static final long serialVersionUID = -4104633972991191369L;
	
	public abstract TriggerResult onElement(T element, long timestamp, W window, TriggerContext ctx) throws Exception;
	
	public abstract TriggerResult onProcessingTime(long time, W window, TriggerContext ctx) throws Exception;
	
	public abstract TriggerResult onEventTime(long time, W window, TriggerContext ctx) throws Exception;
	
	public boolean canMerge() {
		return false;
	}
	
	public void onMerge(W window, OnMergeContext ctx) throws Exception {
		throw new UnsupportedOperationException("This trigger does not support merging.");
	}
	
	public abstract void clear(W window, TriggerContext ctx) throws Exception;
	
	public interface TriggerContext {
		long getCurrentProcessingTime();
		MetricGroup getMetricGroup();
		long getCurrentWatermark();
		void registerProcessingTimeTimer(long time);
		void registerEventTimeTimer(long time);
		void deleteProcessingTimeTimer(long time);
		void deleteEventTimeTimer(long time);
		<S extends State> S getPartitionedState(StateDescriptor<S, ?> stateDescriptor);
		@Deprecated
		<S extends Serializable> ValueState<S> getKeyValueState(String name, Class<S> stateType, S defaultState);
		@Deprecated
		<S extends Serializable> ValueState<S> getKeyValueState(String name, TypeInformation<S> stateType, S defaultState);	
	}
	
	public interface OnMergeContext extends TriggerContext {
		<S extends MergingState<?, ?>> void mergePartitionedState(StateDescriptor<S, ?> stateDescriptor);
	}
}
```
当元素加入窗口时，onElement方法被调用，主要完成定时器的注册，当基于processing time的timer被触发后，onProcessingTime方法被调用，同样的，当基于event time的timer被触发后，onEventTime方法被调用。

其实现类有以下几种：

![](Flink的Window源码剖析_files/7.jpg)

常用的主要有CountTrigger、EventTimeTrigger、ProcessingTimeTrigger，下面分别看看这几个触发器。

### CountTrigger

```
public class CountTrigger<W extends Window> extends Trigger<Object, W> {
	private static final long serialVersionUID = 1L;
	
	// 当窗口中的元素>=maxCount时，窗口操作将被触发
	private final long maxCount;
	
	// 声明count的ReducingStateDescriptor，相当于一个分布式环境下的共享变量，主要记录当前窗口中元素的数量
	private final ReducingStateDescriptor<Long> stateDesc =
			new ReducingStateDescriptor<>("count", new Sum(), LongSerializer.INSTANCE);

	private CountTrigger(long maxCount) {
		this.maxCount = maxCount;
	}

	@Override
	public TriggerResult onElement(Object element, long timestamp, W window, TriggerContext ctx) throws Exception {
		// 获取窗口中的元素的数量
		ReducingState<Long> count = ctx.getPartitionedState(stateDesc);
		// 执行+1操作
		count.add(1L);
		// 若count值>=maxCount，则窗口执行fire操作，否则continue
		if (count.get() >= maxCount) {
			count.clear();
			return TriggerResult.FIRE;
		}
		return TriggerResult.CONTINUE;
	}

	@Override
	public TriggerResult onEventTime(long time, W window, TriggerContext ctx) {
		return TriggerResult.CONTINUE;
	}

	@Override
	public TriggerResult onProcessingTime(long time, W window, TriggerContext ctx) throws Exception {
		return TriggerResult.CONTINUE;
	}

	@Override
	public void clear(W window, TriggerContext ctx) throws Exception {
		// 清空共享变量
		ctx.getPartitionedState(stateDesc).clear();
	}

	@Override
	public boolean canMerge() {
		return true;
	}

	@Override
	public void onMerge(W window, OnMergeContext ctx) throws Exception {
		// 当发生窗口merge时，合并共享变量
		ctx.mergePartitionedState(stateDesc);
	}

	@Override
	public String toString() {
		return "CountTrigger(" +  maxCount + ")";
	}
	
	// 提供of(long maxCount)方法返回CountTrigger的实例
	public static <W extends Window> CountTrigger<W> of(long maxCount) {
		return new CountTrigger<>(maxCount);
	}

	private static class Sum implements ReduceFunction<Long> {
		private static final long serialVersionUID = 1L;

		@Override
		public Long reduce(Long value1, Long value2) throws Exception {
			return value1 + value2;
		}
	}
}
```

### ProcessingTimeTrigger

当processing time超过窗口的end值时，窗口将执行fire操作：

```
@Override
public TriggerResult onElement(Object element, long timestamp, TimeWindow window, TriggerContext ctx) {
	// 注册processing time的时钟，相当于定了个闹钟，闹钟时间为window.maxTimestamp()
	ctx.registerProcessingTimeTimer(window.maxTimestamp());
	return TriggerResult.CONTINUE;
}

@Override
public TriggerResult onEventTime(long time, TimeWindow window, TriggerContext ctx) throws Exception {
	// 不做处理
	return TriggerResult.CONTINUE;
}

@Override
public TriggerResult onProcessingTime(long time, TimeWindow window, TriggerContext ctx) {
	// 当闹钟定的时间到了，执行fire操作
	return TriggerResult.FIRE;
}

@Override
public void clear(TimeWindow window, TriggerContext ctx) throws Exception {
	// 清除时钟
	ctx.deleteProcessingTimeTimer(window.maxTimestamp());
}
```

可以看到，onElement方法主要用于注册时钟，时钟时间到达后(onProcessingTime)，会触发窗口的fire操作。

### EventTimeTrigger

EventTimeTrigger与ProcessingTimeTrigger类似：

```
@Override
public TriggerResult onElement(Object element, long timestamp, TimeWindow window, TriggerContext ctx) throws Exception {
	if (window.maxTimestamp() <= ctx.getCurrentWatermark()) {
		// if the watermark is already past the window fire immediately
		return TriggerResult.FIRE;
	} else {
		ctx.registerEventTimeTimer(window.maxTimestamp());
		return TriggerResult.CONTINUE;
	}
}

@Override
public TriggerResult onEventTime(long time, TimeWindow window, TriggerContext ctx) {
	return time == window.maxTimestamp() ?
		TriggerResult.FIRE :
		TriggerResult.CONTINUE;
}

@Override
public TriggerResult onProcessingTime(long time, TimeWindow window, TriggerContext ctx) throws Exception {
	return TriggerResult.CONTINUE;
}

@Override
public void clear(TimeWindow window, TriggerContext ctx) throws Exception {
	ctx.deleteEventTimeTimer(window.maxTimestamp());
}
```

onElement方法会检查当前窗口的最大时间戳是否大于水印，若大于水印，则直接触发窗口的fire操作，若小于水印，则注册EventTime的时钟，当**水印时间到达时钟设定值**时，会触发窗口的fire操作。

最后，我们看一个很有用的Trigger工具类--PurgingTrigger。

### PurgingTrigger

首先看PurgingTrigger的构造器：

```
private PurgingTrigger(Trigger<T, W> nestedTrigger) {
	this.nestedTrigger = nestedTrigger;
}
```

PurgingTrigger会将传入的nestedTrigger进行"二次处理"，当nestedTrigger执行fire操作时，PurgingTrigger会将其转换为fire+purge操作。

```
@Override
public TriggerResult onElement(T element, long timestamp, W window, TriggerContext ctx) throws Exception {
	TriggerResult triggerResult = nestedTrigger.onElement(element, timestamp, window, ctx);
	return triggerResult.isFire() ? TriggerResult.FIRE_AND_PURGE : triggerResult;
}

@Override
public TriggerResult onEventTime(long time, W window, TriggerContext ctx) throws Exception {
	TriggerResult triggerResult = nestedTrigger.onEventTime(time, window, ctx);
	return triggerResult.isFire() ? TriggerResult.FIRE_AND_PURGE : triggerResult;
}

@Override
public TriggerResult onProcessingTime(long time, W window, TriggerContext ctx) throws Exception {
	TriggerResult triggerResult = nestedTrigger.onProcessingTime(time, window, ctx);
	return triggerResult.isFire() ? TriggerResult.FIRE_AND_PURGE : triggerResult;
}
```

**典型的修饰器模式。**

KeyedStream的countWindow(long size)直接应用了PurgingTrigger工具类。

```
public WindowedStream<T, KEY, GlobalWindow> countWindow(long size) {
	return window(GlobalWindows.create()).trigger(PurgingTrigger.of(CountTrigger.of(size)));
}
```

## Evictor

可以译为“驱逐者”。在Trigger触发之后，在窗口被处理前/后，Evictor（如果有Evictor的话）会用来剔除窗口中不需要的元素，相当于一个filter。

```
public interface Evictor<T, W extends Window> extends Serializable {

	void evictBefore(Iterable<TimestampedValue<T>> elements, int size, W window, EvictorContext evictorContext);

	void evictAfter(Iterable<TimestampedValue<T>> elements, int size, W window, EvictorContext evictorContext);

	interface EvictorContext {
		
		long getCurrentProcessingTime();

		MetricGroup getMetricGroup();

		long getCurrentWatermark();
	}
}
```

其主要有如下实现类：

![](Flink的Window源码剖析_files/8.jpg)

### CountEvictor

```
public class CountEvictor<W extends Window> implements Evictor<Object, W> {
	private static final long serialVersionUID = 1L;
	
	// 窗口的最大元素数量
	private final long maxCount;
	// 是否在计算后驱逐元素
	private final boolean doEvictAfter;

	private CountEvictor(long count, boolean doEvictAfter) {
		this.maxCount = count;
		this.doEvictAfter = doEvictAfter;
	}

	private CountEvictor(long count) {
		this.maxCount = count;
		// 默认在计算前执行Evictor操作
		this.doEvictAfter = false;
	}

	@Override
	public void evictBefore(Iterable<TimestampedValue<Object>> elements, int size, W window, EvictorContext ctx) {
		// 若doEvictAfter为false
		if (!doEvictAfter) {
			evict(elements, size, ctx);
		}
	}

	@Override
	public void evictAfter(Iterable<TimestampedValue<Object>> elements, int size, W window, EvictorContext ctx) {
		// 若doEvictAfter为true
		if (doEvictAfter) {
			evict(elements, size, ctx);
		}
	}

	private void evict(Iterable<TimestampedValue<Object>> elements, int size, EvictorContext ctx) {
		if (size <= maxCount) {
			return;
		} else {
			int evictedCount = 0;
			// Iterator<TimestampedValue<Object>> iterator是按照元素到达时间排序的有序迭代器，第1个元素为时间最old的
			for (Iterator<TimestampedValue<Object>> iterator = elements.iterator(); iterator.hasNext();){
				iterator.next();
				evictedCount++;
				if (evictedCount > size - maxCount) {
					break;
				} else {
					// 将多余的元素剔除
					iterator.remove();
				}
			}
		}
	}

	/**
	 * Creates a {@code CountEvictor} that keeps the given number of elements.
	 * Eviction is done before the window function.
	 *
	 * @param maxCount The number of elements to keep in the pane.
	 */
	public static <W extends Window> CountEvictor<W> of(long maxCount) {
		return new CountEvictor<>(maxCount);
	}

	public static <W extends Window> CountEvictor<W> of(long maxCount, boolean doEvictAfter) {
		return new CountEvictor<>(maxCount, doEvictAfter);
	}
}
```

KeyedStream的countWindow(long size, long slide)方法应用CountEvictor实现了滑动窗口。

```
public WindowedStream<T, KEY, GlobalWindow> countWindow(long size, long slide) {
	return window(GlobalWindows.create())
			.evictor(CountEvictor.of(size))
			.trigger(CountTrigger.of(slide));
}
```

### TimeEvictor

TimeEvictor与CountEvictor类似，TimeEvictor基于时间驱动，故其windowSize参数对应CountEvictor的maxCount，均表征窗口的大小，重点看一下evict方法：

```
private void evict(Iterable<TimestampedValue<Object>> elements, int size, EvictorContext ctx) {
	if (!hasTimestamp(elements)) {
		return;
	}
	// 获取所有元素时间戳的最大值
	long currentTime = getMaxTimestamp(elements);
	// 获取evict分割线
	long evictCutoff = currentTime - windowSize;

	for (Iterator<TimestampedValue<Object>> iterator = elements.iterator(); iterator.hasNext(); ) {
		TimestampedValue<Object> record = iterator.next();
		// 若元素的时间戳不大于evict分割线，执行删除操作
		if (record.getTimestamp() <= evictCutoff) {
			iterator.remove();
		}
	}
}
```

### DeltaEvictor

```
public class DeltaEvictor<T, W extends Window> implements Evictor<T, W> {
	private static final long serialVersionUID = 1L;
	// 求两元素间距离的函数
	DeltaFunction<T> deltaFunction;
	// 阈值
	private double threshold;
	private final boolean doEvictAfter;
	
	......
	
	private void evict(Iterable<TimestampedValue<T>> elements, int size, EvictorContext ctx) {
		// 获取最新的元素
		TimestampedValue<T> lastElement = Iterables.getLast(elements);
		for (Iterator<TimestampedValue<T>> iterator = elements.iterator(); iterator.hasNext();){
			TimestampedValue<T> element = iterator.next();
			// 若当前元素与最新元素之间的距离超过阈值，则删除该元素
			if (deltaFunction.getDelta(element.getValue(), lastElement.getValue()) >= this.threshold) {
				iterator.remove();
			}
		}
	}
		
	......
	
}
```

由上述代码易知，基于DeltaEvictor可以实现CountEvictor和TimeEvictor，只要实现各自的DeltaFunction即可，所以DeltaEvictor更具有一般性。
