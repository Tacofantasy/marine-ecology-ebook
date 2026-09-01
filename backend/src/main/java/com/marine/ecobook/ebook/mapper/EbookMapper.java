package com.marine.ecobook.ebook.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marine.ecobook.ebook.model.Ebook;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EbookMapper extends BaseMapper<Ebook> {

    @Select("""
            SELECT COUNT(*)
            FROM chapters
            WHERE ebook_id = #{ebookId}
              AND CHAR_LENGTH(TRIM(REGEXP_REPLACE(content, '<[^>]*>', ''))) > 0
            """)
    long countNonEmptyChapters(@Param("ebookId") long ebookId);
}
