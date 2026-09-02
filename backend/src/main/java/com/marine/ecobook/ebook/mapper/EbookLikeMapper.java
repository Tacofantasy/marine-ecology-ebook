package com.marine.ecobook.ebook.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marine.ecobook.ebook.model.EbookLike;
import java.util.Collection;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EbookLikeMapper extends BaseMapper<EbookLike> {

    /**
     * 幂等插入：唯一键 (user_id, ebook_id) 冲突时静默忽略，保证并发下至多一行。
     */
    @Insert("INSERT IGNORE INTO likes (user_id, ebook_id) VALUES (#{userId}, #{ebookId})")
    int insertIgnore(@Param("userId") long userId, @Param("ebookId") long ebookId);

    @Delete("DELETE FROM likes WHERE user_id = #{userId} AND ebook_id = #{ebookId}")
    int deleteByUserAndEbook(@Param("userId") long userId, @Param("ebookId") long ebookId);

    @Select("SELECT COUNT(*) FROM likes WHERE user_id = #{userId} AND ebook_id = #{ebookId}")
    long countByUserAndEbook(@Param("userId") long userId, @Param("ebookId") long ebookId);

    @Select("SELECT COUNT(*) FROM likes WHERE ebook_id = #{ebookId}")
    long countByEbookId(@Param("ebookId") long ebookId);

    /**
     * 批量聚合点赞数，用于列表页避免 N+1 查询。未命中的电子书由调用方按 0 处理。
     */
    @Select("""
            <script>
            SELECT ebook_id AS ebookId, COUNT(*) AS likeCount
            FROM likes
            WHERE ebook_id IN
            <foreach collection="ebookIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY ebook_id
            </script>
            """)
    java.util.List<EbookLikeCount> countByEbookIds(@Param("ebookIds") Collection<Long> ebookIds);
}
