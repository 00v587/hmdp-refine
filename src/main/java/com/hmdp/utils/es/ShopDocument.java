package com.hmdp.utils.es;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.GeoPointField;

import java.time.LocalDateTime;
import java.util.Date;

@Data
@Document(indexName = "shops")
public class ShopDocument {
    @Id
    private Long id;

    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String name;

    @Field(type = FieldType.Long)
    private Long typeId;

    @Field(type = FieldType.Text)
    private String images;

    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String area;

    @Field(type = FieldType.Text)
    private String address;

    // 使用现有x,y字段构建地理位置 - 关键调整！
    @GeoPointField
    private String location; // 格式: "经度,纬度"

    @Field(type = FieldType.Long)
    private Long avgPrice;

    @Field(type = FieldType.Integer)
    private Integer sold;

    @Field(type = FieldType.Integer)
    private Integer comments;

    @Field(type = FieldType.Integer)
    private Integer score;

    @Field(type = FieldType.Text)
    private String openHours;

    @Field(type = FieldType.Date)
    private LocalDateTime createTime;

    @Field(type = FieldType.Date)
    private LocalDateTime updateTime;
}