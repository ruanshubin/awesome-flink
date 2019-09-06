## 分布式缓存

Flink提供了一个分布式缓存，类似于hadoop，可以使用户在并行函数中很方便的读取本地文件，并把它放在taskmanager节点中，防止task重复拉取。

此缓存的工作机制如下：程序注册一个文件或者目录(本地或者远程文件系统，例如hdfs或者s3)，通过ExecutionEnvironment注册缓存文件并为它起一个名称。

当程序执行，Flink自动将文件或者目录复制到所有taskmanager节点的本地文件系统，仅会执行一次。用户可以通过这个指定的名称查找文件或者目录，然后从taskmanager节点的本地文件系统访问它。

## 示例

在ExecutionEnvironment中注册一个文件：

```
// 获取运行环境
ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();
// 注册一个文件 本地文件或者分布式文件
env.registerCachedFile("flink-common\\src\\main\\resources\\cache.txt", "cache");
```

在用户函数中访问缓存文件或者目录(这里是一个map函数)。这个函数必须继承RichFunction,因为它需要使用RuntimeContext读取数据:

```
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
```

完整代码见：[https://github.com/Ruanshubin/awesome-flink/tree/master/flink-common/src/main/java/com/ruanshubin/bigdata/flink/common/cache](https://github.com/Ruanshubin/awesome-flink/tree/master/flink-common/src/main/java/com/ruanshubin/bigdata/flink/common/cache)

