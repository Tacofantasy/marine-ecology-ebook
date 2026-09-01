package com.marine.ecobook.ebook.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ChapterMapper {

    @Delete("DELETE FROM chapters WHERE ebook_id = #{ebookId}")
    int deleteByEbookId(@Param("ebookId") long ebookId);
}
