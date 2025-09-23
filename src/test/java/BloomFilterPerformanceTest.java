import com.hmdp.HmDianPingApplication;
import com.hmdp.entity.User;
import com.hmdp.service.IUserService;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest(classes = HmDianPingApplication.class)
public class BloomFilterPerformanceTest {

    @Autowired
    private RedissonClient redissonClient;
    
    @Autowired
    private IUserService userService;

    @Test
    public void testWithBloomFilter() {
        RBloomFilter<Long> userBloom = redissonClient.getBloomFilter("user:bloom");
        
        // 测试10000次随机查询
        AtomicInteger bloomFilterHits = new AtomicInteger(0);
        AtomicInteger dbQueries = new AtomicInteger(0);
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < 10000; i++) {
            long randomId = (long) (Math.random() * 100000000);
            
            if (!userBloom.contains(randomId)) {
                bloomFilterHits.incrementAndGet();
            } else {
                // 布隆过滤器认为可能存在，需要查询数据库
                dbQueries.incrementAndGet();
                userService.getById(randomId); // 实际查询数据库
            }
        }
        
        long endTime = System.currentTimeMillis();
        
        System.out.println("=== 布隆过滤器性能测试结果 ===");
        System.out.println("总查询次数: 10000");
        System.out.println("被布隆过滤器拦截的查询次数: " + bloomFilterHits.get());
        System.out.println("实际查询数据库的次数: " + dbQueries.get());
        System.out.println("布隆过滤器拦截率: " + (bloomFilterHits.get() * 100.0 / 10000) + "%");
        System.out.println("耗时: " + (endTime - startTime) + "ms");
    }
    
    @Test
    public void testWithoutBloomFilter() {
        // 模拟没有布隆过滤器的情况，所有请求都查询数据库
        AtomicInteger dbQueries = new AtomicInteger(0);
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < 10000; i++) {
            long randomId = (long) (Math.random() * 100000000);
            
            // 所有请求都查询数据库
            dbQueries.incrementAndGet();
            userService.getById(randomId); // 实际查询数据库
        }
        
        long endTime = System.currentTimeMillis();
        
        System.out.println("=== 无布隆过滤器性能测试结果 ===");
        System.out.println("总查询次数: 10000");
        System.out.println("查询数据库的次数: " + dbQueries.get());
        System.out.println("耗时: " + (endTime - startTime) + "ms");
    }
}