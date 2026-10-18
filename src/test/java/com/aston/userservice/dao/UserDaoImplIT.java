package com.aston.userservice.dao;

import com.aston.userservice.entity.User;
import com.aston.userservice.exception.DaoException;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
class UserDaoImplIT {
    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("user_service_test")
            .withUsername("test")
            .withPassword("test");

    private static SessionFactory sessionFactory;
    private UserDao userDao;

    @BeforeAll
    static void createSessionFactory() {
        // Не читаем hibernate.cfg.xml: все подключения ведут только в контейнер.
        sessionFactory = new Configuration()
                .addAnnotatedClass(User.class)
                .setProperty("hibernate.connection.driver_class", "org.postgresql.Driver")
                .setProperty("hibernate.connection.url", POSTGRES.getJdbcUrl())
                .setProperty("hibernate.connection.username", POSTGRES.getUsername())
                .setProperty("hibernate.connection.password", POSTGRES.getPassword())
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.show_sql", "false")
                .setProperty("hibernate.cache.use_second_level_cache", "false")
                .setProperty("hibernate.cache.use_query_cache", "false")
                .buildSessionFactory();
    }

    @BeforeEach
    void resetDatabase() throws SQLException {
        // DAO сам делает commit, поэтому внешний rollback теста не очистил бы его записи.
        try (Connection connection = connection(); var statement = connection.createStatement()) {
            statement.executeUpdate("TRUNCATE TABLE users RESTART IDENTITY");
        }
        userDao = new UserDaoImpl(sessionFactory);
    }

    @AfterAll
    static void closeSessionFactory() {
        if (sessionFactory != null) { sessionFactory.close(); }
    }

    @Test
    void saveCommitsAllFieldsAndGeneratesIdAndTimestamp() throws SQLException {
        User user = new User("Иван", "ivan@example.com", 25);
        userDao.save(user);
        assertNotNull(user.getId());
        assertNotNull(user.getCreatedAt());
        // Читаем отдельным JDBC-соединением: проверяем реально зафиксированные данные.
        try (Connection connection = connection();
             var statement = connection.prepareStatement("SELECT * FROM users WHERE id = ?")) {
            statement.setLong(1, user.getId());
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals("Иван", result.getString("name"));
                assertEquals("ivan@example.com", result.getString("email"));
                assertEquals(25, result.getInt("age"));
                assertNotNull(result.getTimestamp("created_at"));
                assertFalse(result.next());
            }
        }
    }

    @Test
    void getByIdReadsSeededUser() throws SQLException {
        long id = insertUser("Иван", "ivan@example.com", 25);
        User user = userDao.getById(id);
        assertNotNull(user);
        assertEquals(id, user.getId());
        assertEquals("Иван", user.getName());
        assertEquals("ivan@example.com", user.getEmail());
        assertEquals(25, user.getAge());
        assertNotNull(user.getCreatedAt());
    }

    @Test
    void getByIdReturnsNullWhenMissing() {
        assertNull(userDao.getById(999L));
    }

    @Test
    void getAllReturnsUsersOrderedById() throws SQLException {
        long first = insertUser("Иван", "ivan@example.com", 25);
        long second = insertUser("Пётр", "petr@example.com", 30);
        List<User> users = userDao.getAll();
        assertEquals(List.of(first, second), users.stream().map(User::getId).toList());
        assertEquals(List.of("ivan@example.com", "petr@example.com"),
                users.stream().map(User::getEmail).toList());
    }

    @Test
    void getAllReturnsEmptyListInCleanDatabase() {
        assertTrue(userDao.getAll().isEmpty());
    }

    @Test
    void updateCommitsChangesButPreservesIdAndCreatedAt() throws SQLException {
        long id = insertUser("Иван", "ivan@example.com", 25);
        User user = userDao.getById(id);
        LocalDateTime createdAt = user.getCreatedAt();
        user.setName("Пётр");
        user.setEmail("petr@example.com");
        user.setAge(30);
        assertTrue(userDao.update(user));
        try (Connection connection = connection();
             var statement = connection.prepareStatement("SELECT * FROM users WHERE id = ?")) {
            statement.setLong(1, id);
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(id, result.getLong("id"));
                assertEquals("Пётр", result.getString("name"));
                assertEquals("petr@example.com", result.getString("email"));
                assertEquals(30, result.getInt("age"));
                assertEquals(createdAt, result.getObject("created_at", LocalDateTime.class));
            }
        }
    }

    @Test
    void updateReturnsFalseWhenRecordWasDeleted() throws SQLException {
        long id = insertUser("Иван", "ivan@example.com", 25);
        User detached = userDao.getById(id);
        assertTrue(userDao.delete(id));
        detached.setName("Новое имя");
        assertFalse(userDao.update(detached));
        assertEquals(0, countUsers());
    }

    @Test
    void deleteRemovesOnlyRequestedUser() throws SQLException {
        long id = insertUser("Иван", "ivan@example.com", 25);
        long otherId = insertUser("Пётр", "petr@example.com", 30);
        assertTrue(userDao.delete(id));
        assertEquals(1, countUsers());
        assertNull(userDao.getById(id));
        assertNotNull(userDao.getById(otherId));
    }

    @Test
    void deleteReturnsFalseWhenMissing() throws SQLException {
        assertFalse(userDao.delete(999L));
        assertEquals(0, countUsers());
    }

    @Test
    void duplicateEmailRollsBackInsertAndNextSaveSucceeds() throws SQLException {
        insertUser("Иван", "ivan@example.com", 25);
        DaoException error = assertThrows(DaoException.class,
                () -> userDao.save(new User("Дубликат", "ivan@example.com", 30)));
        assertEquals("23505", sqlState(error));
        assertEquals(1, countUsers());
        User next = new User("Пётр", "petr@example.com", 30);
        userDao.save(next);
        assertEquals(2, countUsers());
        assertEquals("Пётр", userDao.getById(next.getId()).getName());
    }

    @Test
    void duplicateEmailRollsBackEveryUpdatedField() throws SQLException {
        insertUser("Иван", "ivan@example.com", 25);
        long id = insertUser("Пётр", "petr@example.com", 30);
        User user = userDao.getById(id);
        LocalDateTime createdAt = user.getCreatedAt();
        user.setName("Не должно сохраниться");
        user.setAge(99);
        user.setEmail("ivan@example.com");
        DaoException error = assertThrows(DaoException.class, () -> userDao.update(user));
        assertEquals("23505", sqlState(error));
        User unchanged = userDao.getById(id);
        assertEquals("Пётр", unchanged.getName());
        assertEquals("petr@example.com", unchanged.getEmail());
        assertEquals(30, unchanged.getAge());
        assertEquals(createdAt, unchanged.getCreatedAt());
        assertEquals(2, countUsers());
        unchanged.setName("После отката");
        assertTrue(userDao.update(unchanged));
        assertEquals("После отката", userDao.getById(id).getName());
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static long insertUser(String name, String email, int age) throws SQLException {
        try (Connection connection = connection();
             var statement = connection.prepareStatement(
                     "INSERT INTO users (name, email, age, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP) RETURNING id")) {
            statement.setString(1, name);
            statement.setString(2, email);
            statement.setInt(3, age);
            try (var result = statement.executeQuery()) {
                if (!result.next()) { throw new SQLException("INSERT не вернул id"); }
                return result.getLong(1);
            }
        }
    }

    private static long countUsers() throws SQLException {
        try (Connection connection = connection(); var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT count(*) FROM users")) {
            result.next();
            return result.getLong(1);
        }
    }

    private static String sqlState(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) { return sql.getSQLState(); }
        }
        fail("В цепочке причин нет SQLException");
        return null;
    }
}
