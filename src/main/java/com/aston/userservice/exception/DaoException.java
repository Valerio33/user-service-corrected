package com.aston.userservice.exception;

import java.sql.SQLException;
import org.hibernate.JDBCException;

public class DaoException extends RuntimeException {
    public DaoException(String operation, Throwable cause) {
        super(operation + ": " + explain(cause), cause);
    }

    private static String explain(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            String state = null;
            if (cause instanceof SQLException sql) {
                state = sql.getSQLState();
            } else if (cause instanceof JDBCException jdbc) {
                state = jdbc.getSQLState();
            }
            if (state == null) { continue; }
            if (state.equals("23505")) {
                return "нарушена уникальность данных; возможно, этот email уже занят.";
            }
            if (state.startsWith("23")) { return "нарушены ограничения базы данных."; }
            if (state.startsWith("08") || state.startsWith("57")) {
                return "соединение с PostgreSQL недоступно или прервано.";
            }
            if (state.startsWith("40")) { return "конфликт транзакций; повторите операцию."; }
        }
        return "ошибка работы с базой данных. Подробности находятся в журнале.";
    }
}
