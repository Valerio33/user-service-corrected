package com.aston.userservice.dao;

import com.aston.userservice.entity.User;
import com.aston.userservice.exception.DaoException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class UserDaoImpl implements UserDao {
    private static final Logger logger = LoggerFactory.getLogger(UserDaoImpl.class);
    private final SessionFactory sessionFactory;

    public UserDaoImpl(SessionFactory sessionFactory) {
        this.sessionFactory = Objects.requireNonNull(sessionFactory);
    }

    @Override
    public void save(User user) {
        Objects.requireNonNull(user, "Пользователь не должен быть null");
        inTransaction("Не удалось сохранить пользователя", session -> {
            session.persist(user);
            return null;
        });
        logger.info("Пользователь сохранён, id={}", user.getId());
    }

    @Override
    public User getById(Long id) {
        checkId(id);
        return inTransaction("Не удалось найти пользователя", session -> session.get(User.class, id));
    }

    @Override
    public List<User> getAll() {
        return inTransaction("Не удалось получить список пользователей",
                session -> session.createQuery("from User order by id", User.class).list());
    }

    @Override
    public boolean update(User user) {
        Objects.requireNonNull(user, "Пользователь не должен быть null");
        checkId(user.getId());
        boolean updated = inTransaction("Не удалось обновить пользователя", session -> {
            User existing = session.get(User.class, user.getId());
            if (existing == null) { return false; }
            existing.setName(user.getName());
            existing.setEmail(user.getEmail());
            existing.setAge(user.getAge());
            // existing управляется этой сессией: изменения сохранятся при commit.
            return true;
        });
        if (updated) { logger.info("Пользователь обновлён, id={}", user.getId()); }
        return updated;
    }

    @Override
    public boolean delete(Long id) {
        checkId(id);
        boolean deleted = inTransaction("Не удалось удалить пользователя", session -> {
            User user = session.get(User.class, id);
            if (user == null) { return false; }
            session.remove(user);
            return true;
        });
        if (deleted) { logger.info("Пользователь удалён, id={}", id); }
        return deleted;
    }

    private <T> T inTransaction(String operation, Function<Session, T> action) {
        try (Session session = sessionFactory.openSession()) {
            Transaction transaction = null;
            try {
                transaction = session.beginTransaction();
                T result = action.apply(session);
                transaction.commit();
                return result;
            } catch (RuntimeException error) {
                // Этот catch находится внутри try-with-resources: сессия ещё открыта.
                if (transaction != null) {
                    try {
                        if (transaction.isActive()) { transaction.rollback(); }
                    } catch (RuntimeException rollbackError) {
                        error.addSuppressed(rollbackError);
                    }
                }
                throw error;
            }
        } catch (RuntimeException error) {
            logger.error(operation, error);
            throw new DaoException(operation, error);
        }
    }

    private static void checkId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("ID должен быть положительным целым числом.");
        }
    }
}
