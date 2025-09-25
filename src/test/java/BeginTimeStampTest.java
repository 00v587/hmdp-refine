import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Slf4j
public class BeginTimeStampTest {
    public static void main(String[] args) {
        LocalDateTime time = LocalDateTime.of(2025,9,25,0,0,0);
        long second = time.toEpochSecond(ZoneOffset.UTC);
        log.info("开始时间戳：" + second + "ms");
    }
}
