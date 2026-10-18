package com.aston.userservice.service;

import com.aston.userservice.dao.UserDao;
import com.aston.userservice.entity.User;
import com.aston.userservice.exception.DaoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock
    private UserDao userDao;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userDao);
    }

    @Test
    void createSavesNormalizedUserAndReturnsIt() {
        User result = userService.create("  Иван  ", " ivan@example.com ", 25);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userDao).save(captor.capture());
        User saved = captor.getValue();
        assertSame(saved, result);
        assertAll(
                () -> assertEquals("Иван", saved.getName()),
                () -> assertEquals("ivan@example.com", saved.getEmail()),
                () -> assertEquals(25, saved.getAge()));
        verifyNoMoreInteractions(userDao);
    }

    @ParameterizedTest
    @CsvSource({"'',ivan@example.com,25", "Иван,broken,25",
            "Иван,ivan@example.com,-1", "Иван,ivan@example.com,151"})
    void createRejectsInvalidValuesWithoutCallingDao(String name, String email, int age) {
        assertThrows(IllegalArgumentException.class, () -> userService.create(name, email, age));
        verifyNoInteractions(userDao);
    }

    @Test
    void getByIdReturnsFoundUser() {
        User expected = new User("Иван", "ivan@example.com", 25);
        when(userDao.getById(7L)).thenReturn(expected);
        assertSame(expected, userService.getById(7L));
        verify(userDao).getById(7L);
    }

    @Test
    void getByIdReturnsNullForMissingUser() {
        when(userDao.getById(7L)).thenReturn(null);
        assertNull(userService.getById(7L));
        verify(userDao).getById(7L);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void invalidIdIsRejectedBeforeAnyDaoCall(Long id) {
        assertThrows(IllegalArgumentException.class, () -> userService.getById(id));
        assertThrows(IllegalArgumentException.class, () -> userService.delete(id));
        assertThrows(IllegalArgumentException.class,
                () -> userService.update(id, "Иван", "ivan@example.com", 25));
        verifyNoInteractions(userDao);
    }

    @Test
    void getAllReturnsUsers() {
        List<User> expected = List.of(new User("Иван", "ivan@example.com", 25));
        when(userDao.getAll()).thenReturn(expected);
        assertEquals(expected, userService.getAll());
        verify(userDao).getAll();
    }

    @Test
    void getAllReturnsEmptyListWhenThereAreNoUsers() {
        when(userDao.getAll()).thenReturn(List.of());
        assertTrue(userService.getAll().isEmpty());
        verify(userDao).getAll();
    }

    @Test
    void updateChangesFieldsAndCallsDao() {
        User existing = new User("Иван", "ivan@example.com", 25);
        when(userDao.getById(7L)).thenReturn(existing);
        when(userDao.update(existing)).thenReturn(true);
        assertTrue(userService.update(7L, "  Пётр  ", " petr@example.com ", 30));
        assertAll(
                () -> assertEquals("Пётр", existing.getName()),
                () -> assertEquals("petr@example.com", existing.getEmail()),
                () -> assertEquals(30, existing.getAge()));
        var order = inOrder(userDao);
        order.verify(userDao).getById(7L);
        order.verify(userDao).update(existing);
        verifyNoMoreInteractions(userDao);
    }

    @Test
    void updateMissingUserDoesNotCallDaoUpdate() {
        when(userDao.getById(7L)).thenReturn(null);
        assertFalse(userService.update(7L, "Пётр", "petr@example.com", 30));
        verify(userDao).getById(7L);
        verify(userDao, never()).update(any());
    }

    @Test
    void updateReturnsFalseIfUserWasDeletedAfterLookup() {
        User existing = new User("Иван", "ivan@example.com", 25);
        when(userDao.getById(7L)).thenReturn(existing);
        when(userDao.update(existing)).thenReturn(false);
        assertFalse(userService.update(7L, "Пётр", "petr@example.com", 30));
        verify(userDao).update(existing);
    }

    @ParameterizedTest
    @CsvSource({"'',ivan@example.com,25", "Иван,broken,25",
            "Иван,ivan@example.com,-1", "Иван,ivan@example.com,151"})
    void invalidUpdateDoesNotCallDao(String name, String email, int age) {
        assertThrows(IllegalArgumentException.class,
                () -> userService.update(7L, name, email, age));
        verifyNoInteractions(userDao);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void deleteReturnsDaoResult(boolean expected) {
        when(userDao.delete(7L)).thenReturn(expected);
        assertEquals(expected, userService.delete(7L));
        verify(userDao).delete(7L);
        verifyNoMoreInteractions(userDao);
    }

    @Test
    void createPropagatesDaoFailure() {
        DaoException failure = failure();
        doThrow(failure).when(userDao).save(any(User.class));
        assertSame(failure, assertThrows(DaoException.class,
                () -> userService.create("Иван", "ivan@example.com", 25)));
    }

    @Test
    void getByIdDoesNotHideDaoFailureAsMissingUser() {
        DaoException failure = failure();
        when(userDao.getById(7L)).thenThrow(failure);
        assertSame(failure, assertThrows(DaoException.class, () -> userService.getById(7L)));
    }

    @Test
    void getAllDoesNotHideDaoFailureAsEmptyList() {
        DaoException failure = failure();
        when(userDao.getAll()).thenThrow(failure);
        assertSame(failure, assertThrows(DaoException.class, () -> userService.getAll()));
    }

    @Test
    void updatePropagatesDaoFailure() {
        User existing = new User("Иван", "ivan@example.com", 25);
        DaoException failure = failure();
        when(userDao.getById(7L)).thenReturn(existing);
        when(userDao.update(existing)).thenThrow(failure);
        assertSame(failure, assertThrows(DaoException.class,
                () -> userService.update(7L, "Пётр", "petr@example.com", 30)));
    }

    @Test
    void deletePropagatesDaoFailure() {
        DaoException failure = failure();
        when(userDao.delete(7L)).thenThrow(failure);
        assertSame(failure, assertThrows(DaoException.class, () -> userService.delete(7L)));
    }

    private static DaoException failure() {
        return new DaoException("Тестовая ошибка", new SQLException("Connection lost", "08006"));
    }
}
