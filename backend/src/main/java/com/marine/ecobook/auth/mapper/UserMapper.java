package com.marine.ecobook.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marine.ecobook.auth.model.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
