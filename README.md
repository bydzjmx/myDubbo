## 利用Netty手写简易dubbo
### 1.什么是RPC？
只要客户端和服务端不在同一个进程，即跨进程都是远程过程调用
![image](https://note.youdao.com/yws/public/resource/ec2946bb9d3186ffa237b18a948304b8/xmlnote/9C3F09882EFA473B818A62A3D6F2DBE4/10106)
### 2. RPC的实现组件？
a. rmi/web service/http/Hessian   
b. dubbo/spring cloud/thrift

### 3. RPC框架如何实现分布式环境的远程调用？
在一个典型RPC使用场景中,包含了服务发现、负载、容错、网络传输、序列化等组件，其中RPC协议指明了程序如何进行网络传输和序列化。透明化：中间过程透明，直接相当于client实现本地方法，将中间过程封装

### 4. 实现原理图
![image](https://note.youdao.com/yws/public/resource/ec2946bb9d3186ffa237b18a948304b8/xmlnote/9948F21931D14BCDB91E55BAEE46641D/10110)

### 5. 实现过程：
1. 实现RPC远程传输（底层：Socket传输字节流）
    1. http服务实现（通过netty 实现http服务）

```java
public void openServer(int port) throws InterruptedException {
        //设置启动项，主从线程池
        ServerBootstrap bootstrap = new ServerBootstrap();
        NioEventLoopGroup bossGroup = new NioEventLoopGroup();
        NioEventLoopGroup workGroup = new NioEventLoopGroup();
        //配置bootstrap
        bootstrap.group(bossGroup,workGroup)
                .channel(NioServerSocketChannel.class)
                //设置handler，用于处理channel的请求
                .childHandler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel nsc) throws Exception {
                        //初始化netty管道
                        nsc.pipeline().addLast("http-server",new HttpServerCodec());
                        nsc.pipeline().addLast("http-aggregator",new HttpObjectAggregator(650000));
                        nsc.pipeline().addLast("servlet",new HttpServletHandler());
                    }
                });
        ChannelFuture future = bootstrap.bind(port).sync();
        System.out.println("服务已开启:"+port);
        future.channel().closeFuture().sync();
    }
```
    2. 参数解析成java对象(反序列化)-----JSON

```java
/**
     * 将传递过来的参数进行反序列化获取成java对象,然后进行封装成数组
     * @param method 方法
     * @param byteBuf 字节容器，包含方法的参数
     * @return
     */
    private Object[] deSerializationArgs(Method method, ByteBuf byteBuf){
        //读取方法的参数
        byte[] bytes = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(bytes);

        //存储参数值
        Map<String, Object> map = JsonUtils.parseMap(new String(bytes),String.class,Object.class);
        //获取参数值，id--》  user--》
        String[] names = Arrays.stream(method.getParameters())
                .map(a->a.getName())
                .collect(Collectors.toList())
                .toArray(new String[0]);
        Class<?>[] types = method.getParameterTypes();
        Object args[] = new Object[names.length];
        for (int i = 0; i < names.length; i++) {
            args[i] = JsonUtils.convertValue(map.get(names[i]),types[i]);
        }
        return args;
    }
```
    3. 找到对应的服务方法-----http://host:port/{com.xx.UserService}/{getUser}

```java
//从serverBeans中找到对应的服务bean和方法
    public Function<ByteBuf,Object> findServer(String serverName,String methodName){
        Object server = serverBeans.stream().filter(a->a.getClass().getName().equalsIgnoreCase(serverName))
                .findFirst()
                .orElseThrow(()->new IllegalArgumentException("不能找到"+serverName));
        Method method = Arrays.stream(server.getClass().getMethods())
                .filter(a->a.getName().equalsIgnoreCase(methodName))
                .findFirst()
                .orElseThrow(()->new IllegalArgumentException("不能找到"+methodName));
        return args -> {
            try {
                return method.invoke(server, deSerializationArgs(method, args));
            } catch (Exception e) {
               throw new RuntimeException();
            }
        };
    }
```
    4. 调用服务方法-----反射

```java
//定义servlet处理程序
    public class HttpServletHandler extends SimpleChannelInboundHandler<FullHttpRequest>{
        //操作缓冲区进行读写任务
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {
            //获取对应的服务方法
            URI uri = new URI(msg.uri());
            //分别获取serverName和方法名
            String serverName = uri.getPath().substring(1).split("/")[0];
            String methodName = uri.getPath().substring(1).split("/")[1];
            Object result = findServer(serverName, methodName).apply(msg.content());
            //进行业务处理,设置response
            System.out.println(msg);
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE,"text-html;charset=utf-8");
            //把结果写出去
            response.content().writeBytes(JsonUtils.serialize(result).getBytes());
            //写回给客户端
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
    }
```
    5. 将返回结果进行序列化并返回----JSON
    6. 测试
```
public static void main(String[] args) throws InterruptedException {
        NettyServer nettyServer = new NettyServer();
        nettyServer.serverBeans.add(new UserServiceImpl());
        nettyServer.openServer(8081);
    }
```
可以正常调用UserServiceImpl的方法，并返回结果，RPC通信成功
![image](https://note.youdao.com/yws/public/resource/ec2946bb9d3186ffa237b18a948304b8/xmlnote/301E4F0329DE4DCC8E98001081945BC6/10609)

2. TODO 实现服务发现和注册-----利用zookeeper
3. TODO 实现透明（利用动态代理调用）
4. TODO 实现负载均衡
5. TODO 实现容错