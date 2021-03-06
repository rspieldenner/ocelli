package netflix.ocelli.rxnetty;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.protocol.http.server.HttpServer;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.server.RequestHandler;

import java.util.concurrent.TimeUnit;

import netflix.ocelli.Host;
import netflix.ocelli.LoadBalancer;
import netflix.ocelli.MembershipEvent;
import netflix.ocelli.MembershipEvent.EventType;
import netflix.ocelli.MembershipFailureDetector;
import netflix.ocelli.loadbalancer.RoundRobinLoadBalancer;
import netflix.ocelli.stats.NullAverage;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import rx.Observable;
import rx.functions.Func1;

/**
 * @author Nitesh Kant
 */
public class RxNettyIntegrationTest {

    private HttpServer<ByteBuf, ByteBuf> httpServer;

    @Before
    public void setUp() throws Exception {
        httpServer = RxNetty.createHttpServer(0, new RequestHandler<ByteBuf, ByteBuf>() {
            @Override
            public Observable<Void> handle(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {
                return response.close();
            }
        });
        httpServer.start();
    }

    @After
    public void tearDown() throws Exception {
        if (null != httpServer) {
            httpServer.shutdown();
        }
    }

    @Test
    public void testSimple() throws Exception {
        final HttpClientPool<ByteBuf, ByteBuf> clientPool = HttpClientPool.newPool();
        Observable<HttpClient<ByteBuf, ByteBuf>> clientSource = Observable
                .just(new Host("127.0.0.1", httpServer.getServerPort()))
                .map(new Func1<Host, HttpClient<ByteBuf, ByteBuf>>() {
                    @Override
                    public HttpClient<ByteBuf, ByteBuf> call(
                            Host host) {
                        return clientPool.getClientForHost(host);
                    }
                });

        final PoolHttpMetricListener poolListener = new PoolHttpMetricListener();
        
        final LoadBalancer<HttpClientHolder<ByteBuf, ByteBuf>> lb =
                RoundRobinLoadBalancer
                    .from(clientSource
                        .map(HttpClientHolder.<ByteBuf, ByteBuf>toHolder(NullAverage.factory(), poolListener))
                        .map(MembershipEvent.<HttpClientHolder<ByteBuf, ByteBuf>>toEvent(EventType.ADD))
                        .lift(MembershipFailureDetector.<HttpClientHolder<ByteBuf, ByteBuf>>builder()
                            .withFailureDetector(new RxNettyFailureDetector<ByteBuf, ByteBuf>())
                            .build()));
        
        HttpClientResponse<ByteBuf> response = lb.flatMap(
                new Func1<HttpClientHolder<ByteBuf, ByteBuf>, Observable<HttpClientResponse<ByteBuf>>>() {
                    @Override
                    public Observable<HttpClientResponse<ByteBuf>> call(HttpClientHolder<ByteBuf, ByteBuf> holder) {
                        return holder.getClient()
                                     .submit(HttpClientRequest.createGet("/"))
                                     .map(new Func1<HttpClientResponse<ByteBuf>, HttpClientResponse<ByteBuf>>() {
                                         @Override
                                         public HttpClientResponse<ByteBuf> call(HttpClientResponse<ByteBuf> response) {
                                             response.ignoreContent();
                                             return response;
                                         }
                                     });
                    }
                }).toBlocking().toFuture().get(1, TimeUnit.MINUTES);

        Assert.assertEquals("Unexpected response status.", HttpResponseStatus.OK, response.getStatus());
    }
}
