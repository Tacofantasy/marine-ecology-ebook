package com.marine.ecobook.ebook.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marine.ecobook.ebook.model.Chapter;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ChapterMapper extends BaseMapper<Chapter> {

    @Delete("DELETE FROM chapters WHERE ebook_id = #{ebookId}")
    int deleteByEbookId(@Param("ebookId") long ebookId);

    @Select("SELECT COALESCE(MAX(sort_order), 0) FROM chapters WHERE ebook_id = #{ebookId}")
    int selectMaxSortOrder(@Param("ebookId") long ebookId);

    @Select("SELECT COUNT(*) FROM chapters WHERE ebook_id = #{ebookId}")
    int countByEbookId(@Param("ebookId") long ebookId);

    @Update("UPDATE chapters SET view_count = view_count + 1 WHERE id = #{chapterId}")
    int incrementViewCount(@Param("chapterId") long chapterId);
}
