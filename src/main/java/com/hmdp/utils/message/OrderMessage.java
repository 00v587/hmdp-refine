package com.hmdp.utils.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
/**
 * 定义消息对象
 *
 */
public class OrderMessage implements Serializable {
    private Long userId;
    private Long voucherId;
}
