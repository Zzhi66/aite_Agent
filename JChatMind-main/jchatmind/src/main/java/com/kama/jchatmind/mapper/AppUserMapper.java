package com.kama.jchatmind.mapper;

import com.kama.jchatmind.model.entity.AppUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AppUserMapper {

    int insert(AppUser appUser);

    AppUser selectById(String id);

    AppUser selectByUsername(String username);

    int updateById(AppUser appUser);
}
