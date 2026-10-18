package com.aston.userservice.service;

import com.aston.userservice.dao.UserDao;
import com.aston.userservice.entity.User;

import java.util.List;
import java.util.Objects;

public class UserService {
    private final UserDao userDao;

    public UserService(UserDao userDao) {
        this.userDao = Objects.requireNonNull(userDao);
    }

    public User create(String name, String email, Integer age) {
        User user = new User(name, email, age);
        userDao.save(user);
        return user;
    }

    public User getById(Long id) {
        checkId(id);
        return userDao.getById(id);
    }

    public List<User> getAll() {
        return userDao.getAll();
    }

    public boolean update(Long id, String name, String email, Integer age) {
        checkId(id);
        // Проверяем все поля до обращения к DAO и изменения объекта.
        User values = new User(name, email, age);
        User user = userDao.getById(id);
        if (user == null) { return false; }
        user.setName(values.getName());
        user.setEmail(values.getEmail());
        user.setAge(values.getAge());
        return userDao.update(user);
    }

    public boolean delete(Long id) {
        checkId(id);
        return userDao.delete(id);
    }

    private static void checkId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("ID должен быть положительным целым числом.");
        }
    }
}
