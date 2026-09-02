package com.marine.ecobook.ebook.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marine.ecobook.ebook.model.Ebook;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface EbookMapper extends BaseMapper<Ebook> {

    @Select("""
            SELECT COUNT(*)
            FROM chapters
            WHERE ebook_id = #{ebookId}
              AND CHAR_LENGTH(TRIM(REGEXP_REPLACE(content, '<[^>]*>', ''))) > 0
            """)
    long countNonEmptyChapters(@Param("ebookId") long ebookId);

    @Update("UPDATE ebooks SET view_count = view_count + 1 WHERE id = #{ebookId}")
    int incrementViewCount(@Param("ebookId") long ebookId);
}
