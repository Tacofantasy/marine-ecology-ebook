package com.marine.ecobook.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marine.ecobook.auth.model.User;
import com.marine.ecobook.auth.model.UserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 管理端用户分页查询：关键词匹配用户名或昵称，只返回当前操作者可管理的未注销账号。
     * 偏移量用 long 接收，避免大页码 int 溢出。
     */
    @Select("""
            <script>
            SELECT * FROM users
            WHERE deleted_at IS NULL
              AND role != 'SUPER_ADMIN'
            <if test="role != null">
                AND role = #{role}
            </if>
            <if test="status != null">
                AND status = #{status}
            </if>
            <if test="keyword != null and keyword != ''">
                AND (username LIKE CONCAT('%', #{keyword}, '%') OR display_name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY id ASC
            LIMIT #{pageSize} OFFSET #{offset}
            </script>
            """)
    java.util.List<User> selectAdminPage(@Param("keyword") String keyword,
                                         @Param("status") Integer status,
                                         @Param("role") UserRole role,
                                         @Param("pageSize") int pageSize,
                                         @Param("offset") long offset);

    @Select("""
            <script>
            SELECT COUNT(*) FROM users
            WHERE deleted_at IS NULL
              AND role != 'SUPER_ADMIN'
            <if test="role != null">
                AND role = #{role}
            </if>
            <if test="status != null">
                AND status = #{status}
            </if>
            <if test="keyword != null and keyword != ''">
                AND (username LIKE CONCAT('%', #{keyword}, '%') OR display_name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            </script>
            """)
    long countAdminPage(@Param("keyword") String keyword,
                        @Param("status") Integer status,
                        @Param("role") UserRole role);
}
