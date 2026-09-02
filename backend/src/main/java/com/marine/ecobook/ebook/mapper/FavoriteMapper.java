package com.marine.ecobook.ebook.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marine.ecobook.ebook.model.Favorite;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {

    /**
     * 幂等插入：唯一键 (user_id, ebook_id) 冲突时静默忽略，保证并发下至多一行。
     */
    @Insert("INSERT IGNORE INTO favorites (user_id, ebook_id) VALUES (#{userId}, #{ebookId})")
    int insertIgnore(@Param("userId") long userId, @Param("ebookId") long ebookId);

    @Delete("DELETE FROM favorites WHERE user_id = #{userId} AND ebook_id = #{ebookId}")
    int deleteByUserAndEbook(@Param("userId") long userId, @Param("ebookId") long ebookId);

    @Select("SELECT COUNT(*) FROM favorites WHERE user_id = #{userId} AND ebook_id = #{ebookId}")
    long countByUserAndEbook(@Param("userId") long userId, @Param("ebookId") long ebookId);

    /**
     * 统计当前用户收藏且仍处于已发布状态的电子书总数，供“我的收藏”分页使用。
     */
    @Select("""
            SELECT COUNT(*)
            FROM favorites f
            JOIN ebooks e ON e.id = f.ebook_id
            WHERE f.user_id = #{userId} AND e.status = 'PUBLISHED'
            """)
    long countPublishedByUserId(@Param("userId") long userId);

    /**
     * 分页查询当前用户收藏且仍处于已发布状态的电子书，
     * 按 favorites.created_at DESC, favorites.id DESC 排序。
     * 点赞数由调用方批量聚合填充，不在本查询中对每行单独 COUNT。
     */
    @Select("""
            SELECT e.id, e.category_id, c.name AS category_name, e.title, e.cover_url, e.summary,
                   e.status, e.published_at, e.updated_at, f.created_at AS favorited_at
            FROM favorites f
            JOIN ebooks e ON e.id = f.ebook_id
            LEFT JOIN categories c ON c.id = e.category_id
            WHERE f.user_id = #{userId} AND e.status = 'PUBLISHED'
            ORDER BY f.created_at DESC, f.id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<FavoriteEbookRow> selectPublishedFavoriteRows(
            @Param("userId") long userId, @Param("limit") int limit, @Param("offset") long offset);
}
