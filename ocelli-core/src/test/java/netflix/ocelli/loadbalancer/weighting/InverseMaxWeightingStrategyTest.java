package netflix.ocelli.loadbalancer.weighting;

import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

import junit.framework.Assert;
import netflix.ocelli.loadbalancer.RandomWeightedLoadBalancer;
import netflix.ocelli.loadbalancer.weighting.InverseMaxWeightingStrategy;
import netflix.ocelli.retry.RetryFailedTestRule;
import netflix.ocelli.retry.RetryFailedTestRule.Retry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import rx.subjects.PublishSubject;

import com.google.common.collect.Lists;

public class InverseMaxWeightingStrategyTest extends BaseWeightingStrategyTest {
    @Rule
    public RetryFailedTestRule retryRule = new RetryFailedTestRule();
    
    @Before 
    public void before() {
    }
    
    @Test(expected=NoSuchElementException.class)
    public void testEmptyClients() throws Throwable {
        PublishSubject<IntClientAndMetrics[]> subject = PublishSubject.create();
        
        RandomWeightedLoadBalancer<IntClientAndMetrics> selector = RandomWeightedLoadBalancer.create(subject, 
                new InverseMaxWeightingStrategy<IntClientAndMetrics>(IntClientAndMetrics.BY_METRIC));
        
        IntClientAndMetrics[] clients = create();
        subject.onNext(clients);
        
        List<Integer> counts = Arrays.<Integer>asList(roundToNearest(simulate(selector, clients.length, 1000), 100));
        Assert.assertEquals(Lists.newArrayList(), counts);
    }
    
    @Test
    @Retry(5)
    public void testOneClient() throws Throwable {
        PublishSubject<IntClientAndMetrics[]> subject = PublishSubject.create();
        
        RandomWeightedLoadBalancer<IntClientAndMetrics> selector = RandomWeightedLoadBalancer.create(subject, 
                new InverseMaxWeightingStrategy<IntClientAndMetrics>(IntClientAndMetrics.BY_METRIC));
        
        IntClientAndMetrics[] clients = create(10);
        subject.onNext(clients);
        
        List<Integer> counts = Arrays.<Integer>asList(roundToNearest(simulate(selector, clients.length, 1000), 100));
        Assert.assertEquals(Lists.newArrayList(1000), counts);
    }
    
    @Test
    @Retry(5)
    public void testEqualsWeights() throws Throwable {
        PublishSubject<IntClientAndMetrics[]> subject = PublishSubject.create();
        
        RandomWeightedLoadBalancer<IntClientAndMetrics> selector = RandomWeightedLoadBalancer.create(subject, 
                new InverseMaxWeightingStrategy<IntClientAndMetrics>(IntClientAndMetrics.BY_METRIC));
        
        IntClientAndMetrics[] clients = create(1,1,1,1);
        subject.onNext(clients);
        
        List<Integer> counts = Arrays.<Integer>asList(roundToNearest(simulate(selector, clients.length, 4000), 100));
        Assert.assertEquals(Lists.newArrayList(1000, 1000, 1000, 1000), counts);
    }
    
    @Test
    @Retry(5)
    public void testDifferentWeights() throws Throwable {
        PublishSubject<IntClientAndMetrics[]> subject = PublishSubject.create();
        
        RandomWeightedLoadBalancer<IntClientAndMetrics> selector = RandomWeightedLoadBalancer.create(subject, 
                new InverseMaxWeightingStrategy<IntClientAndMetrics>(IntClientAndMetrics.BY_METRIC));
        
        IntClientAndMetrics[] clients = create(1,2,3,4);
        subject.onNext(clients);
        
        List<Integer> counts = Arrays.<Integer>asList(roundToNearest(simulate(selector, clients.length, 4000), 100));
        Assert.assertEquals(Lists.newArrayList(1600, 1200, 800, 400), counts);
    }
    
    
    
}
