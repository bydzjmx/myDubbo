package com.jmx.myDubbo;

import com.jmx.myDubbo.example.UserServiceImpl;
import com.jmx.myDubbo.utils.JsonUtils;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Netty配置类,完成RPC远程调用过程
 *      *      1. http服务实现（通过netty 实现http服务）
 *      *     2. 参数解析成java对象(反序列化)-----JSON
 *      *     3. 找到对应的服务方法-----http://host:port/{com.xx.UserService}/{getUser}
 *      *     4. 调用服务方法-----反射
 *      *     5. 将返回结果进行序列化并返回----JSON
 */
public class NettyServer {
    //测试RPC调用，不使用注册中心，将测试bean注册到list中
    List<Object> serverBeans = new ArrayList<>();

    //1.  使用Netty实现http服务
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
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE,"text-html;charset=utf-8");
            //把结果写出去
            response.content().writeBytes(JsonUtils.serialize(result).getBytes());
            //写回给客户端
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
    }
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

    public static void main(String[] args) throws InterruptedException {
        NettyServer nettyServer = new NettyServer();
        nettyServer.serverBeans.add(new UserServiceImpl());
        nettyServer.openServer(8081);
    }
}
