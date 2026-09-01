package com.marine.ecobook.category.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EbookReferenceMapper {

    @Select("SELECT COUNT(*) FROM ebooks WHERE category_id = #{categoryId}")
    long countByCategoryId(@Param("categoryId") long categoryId);
}
