package com.aston.userservice.dao;

import com.aston.userservice.entity.User;
import java.util.List;

public interface UserDao {
    void save(User user);
    User getById(Long id);
    List<User> getAll();
    boolean update(User user);
    boolean delete(Long id);
}
