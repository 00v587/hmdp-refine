import com.hmdp.HmDianPingApplication;
import com.hmdp.entity.User;
import com.hmdp.service.IUserService;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.stream.Collectors;

@SpringBootTest(classes = HmDianPingApplication.class)
public class BloomFilterTest {

    @Autowired
    private RedissonClient redissonClient;
    
    @Autowired
    private IUserService userService;

    @Test
    public void testBloomFilter() {
        // 获取布隆过滤器
        RBloomFilter<Long> userBloom = redissonClient.getBloomFilter("user:bloom");
        
        // 获取所有真实存在的用户ID
        List<Long> realUserIds = userService.list().stream().map(User::getId).collect(Collectors.toList());
        System.out.println("真实存在的用户ID数量: " + realUserIds.size());
        
        // 测试真实存在的用户ID
        int existInBloom = 0;
        for (Long id : realUserIds) {
            if (userBloom.contains(id)) {
                existInBloom++;
            }
        }
        System.out.println("真实存在的用户ID中在布隆过滤器中的数量: " + existInBloom);
        System.out.println("真实存在用户ID的布隆过滤器命中率: " + (existInBloom * 100.0 / realUserIds.size()) + "%");
        
        // 测试一些肯定不存在的用户ID
        int falsePositive = 0;
        int testCount = 10000;
        for (long i = 100000000L; i < 100000000L + testCount; i++) {
            if (userBloom.contains(i)) {
                falsePositive++;
            }
        }
        System.out.println("测试不存在的用户ID数量: " + testCount);
        System.out.println("被布隆过滤器误判为存在的数量: " + falsePositive);
        System.out.println("误判率: " + (falsePositive * 100.0 / testCount) + "%");
    }
}